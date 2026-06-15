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
        Log.d("SyncDebug", "=== MEMULAI PUSH DATA KE SERVER ===")
        val firestore = FirestoreManager()
        supervisorScope {
            launch { pushUsers(firestore) }
            launch { pushMembers(firestore) }
            launch { pushCategories(firestore) }
            launch { pushProducts(firestore) }
            launch { pushSales(firestore) }
        }
    }

    suspend fun pullAllDataFromCloud() = withContext(Dispatchers.IO) {
        Log.d("SyncDebug", "=== MEMULAI PULL DATA DARI SERVER ===")
        pullProducts()
        pullUsers()
        pullCategories()
        pullMembers()
        pullSales()
        pullStockMovements()
    }

    suspend fun pullProducts() = withContext(Dispatchers.IO) {
        try {
            val typeToken = object : TypeToken<List<ProductSyncPayload>>() {}
            val products = VolleyHelper.requestList<ProductSyncPayload>(context, Request.Method.GET, "sync/products", typeToken)
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                val baseStorageUrl = VolleyHelper.BASE_URL.removeSuffix("api/").removeSuffix("/") + "/storage/"
                products.forEach { p ->
                    val resolvedPath = if (p.imagePath.isNullOrEmpty() || p.imagePath == "null") null 
                                     else if (p.imagePath!!.startsWith("http")) p.imagePath 
                                     else baseStorageUrl + p.imagePath
                    db.execSQL("""
                        INSERT OR REPLACE INTO products (id, barcode, name, category, price, stock, purchasePrice, minimumStock, expiredDateEpochMs, imagePath, isSynced, createdAtEpochMs)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, (SELECT imagePath FROM products WHERE id = ?)), 1, ?)
                    """.trimIndent(), arrayOf(p.id, p.barcode, p.name, p.category, p.price, p.stock, p.purchasePrice, p.minimumStock, p.expiredDateEpochMs, resolvedPath, p.id, p.createdAtEpochMs))
                }
                db.setTransactionSuccessful()
                Log.d("SyncDebug", "✅ Pull Products Sukses")
            } finally { db.endTransaction() }
        } catch (e: Exception) { Log.e("SyncDebug", "❌ Gagal pull Products: ${e.message}") }
    }

    suspend fun pullCategories() = withContext(Dispatchers.IO) {
        try {
            val categories = VolleyHelper.requestList<CategorySyncPayload>(context, Request.Method.GET, "sync/categories", object : TypeToken<List<CategorySyncPayload>>() {})
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                categories.forEach { c ->
                    db.execSQL("INSERT OR REPLACE INTO categories (id, name, createdAtEpochMs, isSynced) VALUES (?, ?, ?, 1)", arrayOf(c.id, c.name, c.createdAtEpochMs))
                }
                db.setTransactionSuccessful()
                Log.d("SyncDebug", "✅ Pull Categories Sukses")
            } finally { db.endTransaction() }
        } catch (e: Exception) { Log.e("SyncDebug", "❌ Gagal pull Categories") }
    }

    suspend fun pullSales() = withContext(Dispatchers.IO) {
        try {
            val sales = VolleyHelper.requestList<SaleSyncPayload>(context, Request.Method.GET, "sync/sales", object : TypeToken<List<SaleSyncPayload>>() {})
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                sales.forEach { s ->
                    db.execSQL("""
                        INSERT OR REPLACE INTO sales (id, transactionId, cashierId, subtotal, discount, tax, total, paymentMethod, status, isSynced, createdAtEpochMs)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                    """.trimIndent(), arrayOf(s.id, s.transactionId, s.cashierId, s.subtotal, s.discount, s.tax, s.total, s.paymentMethod, s.status, s.createdAtEpochMs))
                    
                    s.items?.forEach { item ->
                        db.execSQL("""
                            INSERT OR REPLACE INTO sale_items (id, saleId, productId, productName, unitPrice, quantity, lineTotal)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(), arrayOf(item.id, s.id, item.productId, item.productName, item.unitPrice, item.quantity, item.lineTotal))
                    }
                }
                db.setTransactionSuccessful()
                Log.d("SyncDebug", "✅ Pull Sales Sukses (${sales.size} tx)")
            } finally { db.endTransaction() }
        } catch (e: Exception) { Log.e("SyncDebug", "❌ Gagal pull Sales: ${e.message}") }
    }

    suspend fun pullStockMovements() = withContext(Dispatchers.IO) {
        try {
            val typeToken = object : TypeToken<List<StockMovementSyncPayload>>() {}
            val movements = VolleyHelper.requestList<StockMovementSyncPayload>(context, Request.Method.GET, "sync/stock-movements", typeToken)
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                movements.forEach { m ->
                    db.execSQL("""
                        INSERT OR REPLACE INTO stock_movements (id, productId, userId, type, quantityDelta, note, isSynced, createdAtEpochMs)
                        VALUES (?, ?, ?, ?, ?, ?, 1, ?)
                    """.trimIndent(), arrayOf(m.id, m.productId, m.userId, m.type, m.quantityDelta, m.note, m.createdAtEpochMs))
                }
                db.setTransactionSuccessful()
                Log.d("SyncDebug", "✅ Pull Stock Movements Sukses (${movements.size} items)")
            } finally { db.endTransaction() }
        } catch (e: Exception) {
            Log.e("SyncDebug", "❌ Gagal pull Stock Movements: ${e.message}")
        }
    }

    suspend fun pullMembers() = withContext(Dispatchers.IO) {
        try {
            val members = VolleyHelper.requestList<MemberSyncPayload>(context, Request.Method.GET, "sync/members", object : TypeToken<List<MemberSyncPayload>>() {})
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                members.forEach { m ->
                    db.execSQL("INSERT OR REPLACE INTO members (id, memberNo, name, phone, address, isActive, isSynced, createdAtEpochMs) VALUES (?, ?, ?, ?, ?, ?, 1, ?)",
                        arrayOf(m.id, m.memberNo, m.name, m.phone, m.address, m.isActive, m.createdAtEpochMs))
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        } catch (e: Exception) {}
    }

    suspend fun pullUsers() = withContext(Dispatchers.IO) {
        try {
            val users = VolleyHelper.requestList<UserSyncPayload>(context, Request.Method.GET, "sync/users", object : TypeToken<List<UserSyncPayload>>() {})
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                users.forEach { u ->
                    db.execSQL("INSERT OR REPLACE INTO users (id, name, username, passwordHash, salt, role, isActive, needsPasswordReset, isSynced, createdAtEpochMs) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?)",
                        arrayOf(u.id, u.name, u.username, u.passwordHash, u.salt, u.role, u.isActive, u.needsPasswordReset, u.createdAtEpochMs))
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        } catch (e: Exception) {}
    }

    private suspend fun pushProducts(f: FirestoreManager) {
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT * FROM products WHERE isSynced = 0", null).use { cursor ->
            while (cursor.moveToNext()) {
                val p = cursor.toProduct()
                var base64: String? = null
                if (!p.imagePath.isNullOrBlank() && p.imagePath!!.startsWith("/")) {
                    try {
                        val file = java.io.File(p.imagePath!!)
                        if (file.exists()) {
                            val bitmap = android.graphics.BitmapFactory.decodeFile(p.imagePath, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 })
                            if (bitmap != null) {
                                val out = java.io.ByteArrayOutputStream()
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, out)
                                base64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                                bitmap.recycle()
                            }
                        }
                    } catch (e: Exception) {}
                }
                val payload = listOf(ProductSyncPayload(p.id, p.barcode, p.name, p.category, p.price, p.stock, p.minimumStock, p.expiredDateEpochMs, p.imagePath, p.purchasePrice, p.createdAtEpochMs, base64))
                try {
                    val resp = VolleyHelper.requestObject(context, Request.Method.POST, "sync/products", payload)
                    val serverPath = resp?.optString("image_path") ?: resp?.optString("imagePath")
                    dbHelper.writableDatabase.execSQL("UPDATE products SET isSynced = 1, imagePath = COALESCE(?, imagePath) WHERE id = ?", arrayOf(serverPath, p.id))
                    f.syncProduct(p)
                } catch (e: Exception) {
                    Log.e("SyncDebug", "❌ pushProducts Volley error: ${e.message}", e)
                }
            }
        }
    }

    private suspend fun pushUsers(f: FirestoreManager) {}
    private suspend fun pushMembers(f: FirestoreManager) {}
    private suspend fun pushCategories(f: FirestoreManager) {
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT * FROM categories WHERE isSynced = 0", null).use { c ->
            val payloads = mutableListOf<CategorySyncPayload>()
            val ids = mutableListOf<Long>()
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow("id"))
                val name = c.getString(c.getColumnIndexOrThrow("name"))
                val time = c.getLong(c.getColumnIndexOrThrow("createdAtEpochMs"))
                ids.add(id); payloads.add(CategorySyncPayload(id, name, time))
            }
            if (payloads.isNotEmpty()) {
                try {
                    VolleyHelper.requestObject(context, Request.Method.POST, "sync/categories", payloads)
                    ids.forEach { id -> dbHelper.writableDatabase.execSQL("UPDATE categories SET isSynced = 1 WHERE id = ?", arrayOf(id)) }
                    ids.forEachIndexed { i, id -> f.syncCategory(CategoryEntity(id, payloads[i].name, payloads[i].createdAtEpochMs, true)) }
                } catch (e: Exception) {}
            }
        }
    }
    private suspend fun pushSales(f: FirestoreManager) {}

    private fun android.database.Cursor.toProduct() = ProductEntity(getLong(getColumnIndexOrThrow("id")), getString(getColumnIndexOrThrow("barcode")), getString(getColumnIndexOrThrow("name")), getString(getColumnIndexOrThrow("category")), getLong(getColumnIndexOrThrow("price")), getLong(getColumnIndexOrThrow("purchasePrice")), getLong(getColumnIndexOrThrow("stock")), getLong(getColumnIndexOrThrow("minimumStock")), if (isNull(getColumnIndexOrThrow("expiredDateEpochMs"))) null else getLong(getColumnIndexOrThrow("expiredDateEpochMs")), getString(getColumnIndexOrThrow("imagePath")), getInt(getColumnIndexOrThrow("isSynced")) == 1, getLong(getColumnIndexOrThrow("createdAtEpochMs")))
}
