package com.kopdes.kopdesjajar.data.network

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.google.gson.reflect.TypeToken
import com.kopdes.kopdesjajar.data.db.*
import com.kopdes.kopdesjajar.data.firebase.FirestoreManager
import com.kopdes.kopdesjajar.data.model.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

class SyncManager(private val context: Context) {
    private val dbHelper = KoperasiDbHelper(context)

    suspend fun pushAllDataToServer() = withContext(Dispatchers.IO) {
        Log.d("SyncDebug", "=== MEMULAI PUSH DATA KE SERVER (LARAVEL & FIREBASE) ===")
        val firestore = FirestoreManager()
        
        supervisorScope {
            launch { pushUsers(firestore) }
            launch { pushMembers(firestore) }
            launch { pushProducts(firestore) }
            launch { pushSales(firestore) }
            launch { pushPromos(firestore) }
        }
    }

    suspend fun pullAllDataFromCloud() = withContext(Dispatchers.IO) {
        Log.d("SyncDebug", "=== MEMULAI PULL DATA DARI SERVER ===")
        val db = dbHelper.writableDatabase

        // 1. Pull Products (Proteksi Gambar Lokal)
        try {
            val typeToken = object : TypeToken<List<ProductSyncPayload>>() {}
            val products = VolleyHelper.requestList<ProductSyncPayload>(context, Request.Method.GET, "sync/products", typeToken)
            db.beginTransaction()
            try {
                products.forEach { p ->
                    db.execSQL(
                        """
                        INSERT INTO products (id, barcode, name, category, price, stock, purchasePrice, minimumStock, expiredDateEpochMs, imagePath, isSynced, createdAtEpochMs)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                        ON CONFLICT(id) DO UPDATE SET
                            barcode=excluded.barcode, name=excluded.name, category=excluded.category, price=excluded.price, 
                            stock=excluded.stock, purchasePrice=excluded.purchasePrice, minimumStock=excluded.minimumStock, 
                            expiredDateEpochMs=excluded.expiredDateEpochMs,
                            imagePath = CASE 
                                WHEN (excluded.imagePath IS NOT NULL AND excluded.imagePath != '' AND excluded.imagePath != 'null') THEN excluded.imagePath 
                                ELSE products.imagePath END,
                            isSynced=1
                        """.trimIndent(),
                        arrayOf(p.id, p.barcode, p.name, p.category, p.price, p.stock, p.purchasePrice, p.minimumStock, p.expiredDateEpochMs, p.imagePath, p.createdAtEpochMs)
                    )
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        } catch (e: Exception) { Log.e("SyncDebug", "❌ Gagal pull Products: ${e.message}") }

        // 2. Pull Users
        try {
            val typeToken = object : TypeToken<List<UserSyncPayload>>() {}
            val users = VolleyHelper.requestList<UserSyncPayload>(context, Request.Method.GET, "sync/users", typeToken)
            db.beginTransaction()
            try {
                users.forEach { u ->
                    db.execSQL(
                        """
                        INSERT INTO users (id, name, username, passwordHash, salt, role, isActive, needsPasswordReset, isSynced, createdAtEpochMs) 
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                        ON CONFLICT(username) DO UPDATE SET
                            id=excluded.id, name=excluded.name, role=excluded.role, isActive=excluded.isActive, isSynced=1
                        """.trimIndent(),
                        arrayOf(u.id, u.name, u.username, u.passwordHash, u.salt, u.role, u.isActive, u.needsPasswordReset, u.createdAtEpochMs)
                    )
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        } catch (e: Exception) { Log.e("SyncDebug", "❌ Gagal pull Users: ${e.message}") }
    }

    private suspend fun pushProducts(firestore: FirestoreManager) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM products WHERE isSynced = 0", null)
        
        while (cursor.moveToNext()) {
            val p = cursor.toProduct()
            var base64: String? = null
            if (!p.imagePath.isNullOrBlank() && p.imagePath!!.startsWith("/")) {
                try {
                    val file = java.io.File(p.imagePath!!)
                    if (file.exists()) {
                        val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                        val bitmap = android.graphics.BitmapFactory.decodeFile(p.imagePath, options)
                        if (bitmap != null) {
                            val outputStream = java.io.ByteArrayOutputStream()
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, outputStream)
                            base64 = android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
                        }
                    }
                } catch (e: Exception) {}
            }
            
            val payloads = listOf(ProductSyncPayload(p.id, p.barcode, p.name, p.category, p.price, p.stock, p.minimumStock, p.expiredDateEpochMs, p.imagePath, p.purchasePrice, p.createdAtEpochMs, base64))
            
            try {
                val response = VolleyHelper.requestObject(context, Request.Method.POST, "sync/products", payloads)
                val serverImagePath = response?.optString("imagePath")
                if (!serverImagePath.isNullOrEmpty() && serverImagePath != "null") {
                    dbHelper.writableDatabase.execSQL("UPDATE products SET isSynced = 1, imagePath = ? WHERE id = ?", arrayOf(serverImagePath, p.id))
                } else {
                    dbHelper.writableDatabase.execSQL("UPDATE products SET isSynced = 1 WHERE id = ?", arrayOf(p.id))
                }
                firestore.syncProduct(p)
            } catch (e: Exception) { Log.e("SyncDebug", "Gagal push produk ${p.name}") }
        }
        cursor.close()
    }

    private suspend fun pushUsers(firestore: FirestoreManager) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM users WHERE isSynced = 0", null)
        while (cursor.moveToNext()) {
            val u = cursor.toUser()
            firestore.syncUser(u)
            dbHelper.writableDatabase.execSQL("UPDATE users SET isSynced = 1 WHERE id = ?", arrayOf(u.id))
        }
        cursor.close()
    }

    private suspend fun pushMembers(f: FirestoreManager) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM members WHERE isSynced = 0", null)
        while (cursor.moveToNext()) {
            val m = cursor.toMember()
            f.syncMember(m)
            dbHelper.writableDatabase.execSQL("UPDATE members SET isSynced = 1 WHERE id = ?", arrayOf(m.id))
        }
        cursor.close()
    }

    private suspend fun pushSales(f: FirestoreManager) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM sales WHERE isSynced = 0", null)
        while (cursor.moveToNext()) {
            val s = cursor.toSale()
            val items = mutableListOf<SaleItemEntity>()
            db.rawQuery("SELECT * FROM sale_items WHERE saleId = ?", arrayOf(s.id.toString())).use { ic ->
                while (ic.moveToNext()) items.add(ic.toSaleItem())
            }
            f.syncSale(s, items)
            dbHelper.writableDatabase.execSQL("UPDATE sales SET isSynced = 1 WHERE id = ?", arrayOf(s.id))
        }
        cursor.close()
    }

    private suspend fun pushPromos(f: FirestoreManager) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM promos WHERE isSynced = 0", null)
        while (cursor.moveToNext()) {
            val p = cursor.toPromo()
            f.syncPromo(p)
            dbHelper.writableDatabase.execSQL("UPDATE promos SET isSynced = 1 WHERE id = ?", arrayOf(p.id))
        }
        cursor.close()
    }

    // --- CURSOR HELPERS ---
    private fun android.database.Cursor.toProduct() = ProductEntity(getLong(getColumnIndexOrThrow("id")), getString(getColumnIndexOrThrow("barcode")), getString(getColumnIndexOrThrow("name")), getString(getColumnIndexOrThrow("category")), getLong(getColumnIndexOrThrow("price")), getLong(getColumnIndexOrThrow("purchasePrice")), getLong(getColumnIndexOrThrow("stock")), getLong(getColumnIndexOrThrow("minimumStock")), if (isNull(getColumnIndexOrThrow("expiredDateEpochMs"))) null else getLong(getColumnIndexOrThrow("expiredDateEpochMs")), getString(getColumnIndexOrThrow("imagePath")), getInt(getColumnIndexOrThrow("isSynced")) == 1, getLong(getColumnIndexOrThrow("createdAtEpochMs")))
    private fun android.database.Cursor.toUser() = UserEntity(getLong(getColumnIndexOrThrow("id")), getString(getColumnIndexOrThrow("name")), getString(getColumnIndexOrThrow("username")), getString(getColumnIndexOrThrow("passwordHash")), getString(getColumnIndexOrThrow("salt")), Role.valueOf(getString(getColumnIndexOrThrow("role"))), getInt(getColumnIndexOrThrow("isActive")) == 1, getInt(getColumnIndexOrThrow("needsPasswordReset")) == 1, getInt(getColumnIndexOrThrow("isSynced")) == 1, getLong(getColumnIndexOrThrow("createdAtEpochMs")))
    private fun android.database.Cursor.toMember() = MemberEntity(getLong(getColumnIndexOrThrow("id")), getString(getColumnIndexOrThrow("memberNo")), getString(getColumnIndexOrThrow("name")), getString(getColumnIndexOrThrow("phone")), getString(getColumnIndexOrThrow("address")), getInt(getColumnIndexOrThrow("isActive")) == 1, getInt(getColumnIndexOrThrow("isSynced")) == 1, getLong(getColumnIndexOrThrow("createdAtEpochMs")))
    private fun android.database.Cursor.toSale() = SaleEntity(getLong(getColumnIndexOrThrow("id")), getString(getColumnIndexOrThrow("transactionId")), if (isNull(getColumnIndexOrThrow("cashierId"))) null else getLong(getColumnIndexOrThrow("cashierId")), getLong(getColumnIndexOrThrow("subtotal")), getLong(getColumnIndexOrThrow("discount")), getLong(getColumnIndexOrThrow("tax")), getLong(getColumnIndexOrThrow("total")), getString(getColumnIndexOrThrow("paymentMethod")), getString(getColumnIndexOrThrow("status")), getInt(getColumnIndexOrThrow("isSynced")) == 1, getLong(getColumnIndexOrThrow("createdAtEpochMs")))
    private fun android.database.Cursor.toSaleItem() = SaleItemEntity(getLong(getColumnIndexOrThrow("id")), getLong(getColumnIndexOrThrow("saleId")), if (isNull(getColumnIndexOrThrow("productId"))) null else getLong(getColumnIndexOrThrow("productId")), getString(getColumnIndexOrThrow("productName")), getLong(getColumnIndexOrThrow("unitPrice")), getLong(getColumnIndexOrThrow("quantity")), getLong(getColumnIndexOrThrow("lineTotal")))
    private fun android.database.Cursor.toPromo() = PromoEntity(getLong(getColumnIndexOrThrow("id")), getString(getColumnIndexOrThrow("code")), getString(getColumnIndexOrThrow("name")), getString(getColumnIndexOrThrow("description")), getDouble(getColumnIndexOrThrow("discountPercent")), getLong(getColumnIndexOrThrow("validUntilEpochMs")), getString(getColumnIndexOrThrow("promoType")), getLong(getColumnIndexOrThrow("minimumPurchase")), if (isNull(getColumnIndexOrThrow("productId"))) null else getLong(getColumnIndexOrThrow("productId")), getInt(getColumnIndexOrThrow("isSynced")) == 1, getInt(getColumnIndexOrThrow("isActive")) == 1)
}
