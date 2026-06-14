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
        Log.d("SyncDebug", "=== MEMULAI SYNC KE LARAVEL (VOLLEY) & FIREBASE ===")
        val firestore = FirestoreManager()
        
        supervisorScope {
            launch { pushUsers(firestore) }
            launch { pushMembers(firestore) }
            launch { pushCategories(firestore) }
            launch { pushProducts(firestore) }
            launch { pushStockMovements(firestore) }
            launch { pushSales(firestore) }
            launch { pushAuditLogs(firestore) }
            launch { pushPromos(firestore) }
            launch { pushSettings(firestore) }
        }
        
        Log.d("SyncDebug", "=== SEMUA PROSES SYNC SELESAI ===")
    }

    suspend fun pullAllDataFromCloud() = withContext(Dispatchers.IO) {
        Log.d("SyncDebug", "=== MEMULAI PULL DATA DARI SERVER (VOLLEY) & CLOUD ===")
        val db = dbHelper.writableDatabase

        // 1. Pull Users
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
                            id=excluded.id, name=excluded.name, passwordHash=excluded.passwordHash, salt=excluded.salt, 
                            role=excluded.role, isActive=excluded.isActive, needsPasswordReset=excluded.needsPasswordReset, isSynced=1
                        """.trimIndent(),
                        arrayOf(u.id, u.name, u.username, u.passwordHash, u.salt, u.role, u.isActive, u.needsPasswordReset, u.createdAtEpochMs)
                    )
                }
                db.setTransactionSuccessful()
                Log.d("SyncDebug", "✅ Volley: Pull Users Sukses (${users.size})")
            } finally { db.endTransaction() }
        } catch (e: Exception) { Log.e("SyncDebug", "❌ Gagal pull Users: ${e.message}") }

        // 2. Pull Products
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
                            barcode=excluded.barcode, name=excluded.name, category=excluded.category, price=excluded.price, stock=excluded.stock, isSynced=1
                        """.trimIndent(),
                        arrayOf(p.id, p.barcode, p.name, p.category, p.price, p.stock, p.purchasePrice, p.minimumStock, p.expiredDateEpochMs, p.imagePath, p.createdAtEpochMs)
                    )
                }
                db.setTransactionSuccessful()
                Log.d("SyncDebug", "✅ Volley: Pull Products Sukses")
            } finally { db.endTransaction() }
        } catch (e: Exception) { Log.e("SyncDebug", "❌ Gagal pull Products: ${e.message}") }

        // 3. Pull Members
        try {
            val typeToken = object : TypeToken<List<MemberSyncPayload>>() {}
            val members = VolleyHelper.requestList<MemberSyncPayload>(context, Request.Method.GET, "sync/members", typeToken)
            db.beginTransaction()
            try {
                members.forEach { m ->
                    db.execSQL(
                        """
                        INSERT INTO members (id, memberNo, name, phone, address, isActive, isSynced, createdAtEpochMs) 
                        VALUES (?, ?, ?, ?, ?, ?, 1, ?)
                        ON CONFLICT(memberNo) DO UPDATE SET
                            id=excluded.id, name=excluded.name, phone=excluded.phone, address=excluded.address, isActive=excluded.isActive, isSynced=1
                        """.trimIndent(),
                        arrayOf(m.id, m.memberNo, m.name, m.phone, m.address, m.isActive, m.createdAtEpochMs)
                    )
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        } catch (e: Exception) { Log.e("SyncDebug", "❌ Gagal pull Members: ${e.message}") }

        // 4. Pull Sales
        try {
            val typeToken = object : TypeToken<List<SaleSyncPayload>>() {}
            val sales = VolleyHelper.requestList<SaleSyncPayload>(context, Request.Method.GET, "sync/sales", typeToken)
            db.beginTransaction()
            try {
                sales.forEach { s ->
                    val existingId = db.rawQuery("SELECT id FROM sales WHERE transactionId = ?", arrayOf(s.transactionId)).use { c ->
                        if (c.moveToFirst()) c.getLong(0) else null
                    }
                    if (existingId != null) {
                        db.execSQL(
                            "UPDATE sales SET cashierId=?, subtotal=?, discount=?, tax=?, total=?, paymentMethod=?, status=?, isSynced=1, createdAtEpochMs=? WHERE id=?",
                            arrayOf(s.cashierId, s.subtotal, s.discount, s.tax, s.total, s.paymentMethod, s.status, s.createdAtEpochMs, existingId)
                        )
                        db.execSQL("DELETE FROM sale_items WHERE saleId = ?", arrayOf(existingId))
                        s.items.forEach { item ->
                            db.execSQL(
                                "INSERT OR IGNORE INTO sale_items (saleId, productId, productName, unitPrice, quantity, lineTotal) VALUES (?,?,?,?,?,?)",
                                arrayOf(existingId, item.productId, item.productName, item.unitPrice, item.quantity, item.lineTotal)
                            )
                        }
                    } else {
                        db.execSQL(
                            "INSERT INTO sales (transactionId, cashierId, subtotal, discount, tax, total, paymentMethod, status, isSynced, createdAtEpochMs) VALUES (?,?,?,?,?,?,?,?,1,?)",
                            arrayOf(s.transactionId, s.cashierId, s.subtotal, s.discount, s.tax, s.total, s.paymentMethod, s.status, s.createdAtEpochMs)
                        )
                        val newId = db.rawQuery("SELECT id FROM sales WHERE transactionId = ?", arrayOf(s.transactionId)).use { c ->
                            if (c.moveToFirst()) c.getLong(0) else null
                        }
                        if (newId != null) {
                            s.items.forEach { item ->
                                db.execSQL(
                                    "INSERT OR IGNORE INTO sale_items (saleId, productId, productName, unitPrice, quantity, lineTotal) VALUES (?,?,?,?,?,?)",
                                    arrayOf(newId, item.productId, item.productName, item.unitPrice, item.quantity, item.lineTotal)
                                )
                            }
                        }
                    }
                }
                db.setTransactionSuccessful()
                Log.d("SyncDebug", "✅ Volley: Pull Sales Sukses (${sales.size})")
            } finally { db.endTransaction() }
        } catch (e: Exception) { Log.e("SyncDebug", "❌ Gagal pull Sales: ${e.message}") }

        // 5. Pull Promos
        try {
            val typeToken = object : TypeToken<List<PromoSyncPayload>>() {}
            val promos = VolleyHelper.requestList<PromoSyncPayload>(context, Request.Method.GET, "sync/promos", typeToken)
            db.beginTransaction()
            try {
                promos.forEach { p ->
                    val exists = db.rawQuery("SELECT id FROM promos WHERE code = ?", arrayOf(p.code)).use { c -> c.moveToFirst() }
                    if (exists) {
                        db.execSQL(
                            "UPDATE promos SET name=?, description=?, discountPercent=?, validUntilEpochMs=?, isActive=?, isSynced=1 WHERE code=?",
                            arrayOf(p.name, p.description, p.discountPercent, p.validUntilEpochMs, p.isActive, p.code)
                        )
                    } else {
                        db.execSQL(
                            "INSERT INTO promos (code, name, description, discountPercent, validUntilEpochMs, promoType, minimumPurchase, isActive, isSynced) VALUES (?,?,?,?,?,'TRANSACTION',0,?,1)",
                            arrayOf(p.code, p.name, p.description, p.discountPercent, p.validUntilEpochMs, p.isActive)
                        )
                    }
                }
                db.setTransactionSuccessful()
                Log.d("SyncDebug", "✅ Volley: Pull Promos Sukses (${promos.size})")
            } finally { db.endTransaction() }
        } catch (e: Exception) { Log.e("SyncDebug", "❌ Gagal pull Promos: ${e.message}") }

        // 6. Pull Stock Movements
        try {
            val typeToken = object : TypeToken<List<StockMovementSyncPayload>>() {}
            val movements = VolleyHelper.requestList<StockMovementSyncPayload>(context, Request.Method.GET, "sync/movements", typeToken)
            db.beginTransaction()
            try {
                movements.forEach { m ->
                    db.execSQL(
                        """
                        INSERT OR REPLACE INTO stock_movements (id, productId, userId, type, quantityDelta, note, isSynced, createdAtEpochMs)
                        VALUES (?, ?, ?, ?, ?, ?, 1, ?)
                        """.trimIndent(),
                        arrayOf(m.id, m.productId, m.userId, m.type, m.quantityDelta, m.note, m.createdAtEpochMs)
                    )
                }
                db.setTransactionSuccessful()
                Log.d("SyncDebug", "✅ Volley: Pull Stock Movements Sukses (${movements.size})")
            } finally { db.endTransaction() }
        } catch (e: Exception) { Log.e("SyncDebug", "❌ Gagal pull Stock Movements: ${e.message}") }

        // 7. Pull Settings
        try {
            val typeToken = object : TypeToken<List<SettingsSyncPayload>>() {}
            val settingsList = VolleyHelper.requestList<SettingsSyncPayload>(context, Request.Method.GET, "sync/settings", typeToken)
            if (settingsList.isNotEmpty()) {
                val s = settingsList.first()
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO settings (id, koperasiName, koperasiAddress, koperasiPhone, taxPercent, discountPercent, shuParameter, latitude, longitude, isSynced, updatedAtEpochMs)
                    VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                    """.trimIndent(),
                    arrayOf(s.koperasiName, s.koperasiAddress, s.koperasiPhone, s.taxPercent, s.discountPercent, s.shuParameter, s.latitude, s.longitude, s.updatedAtEpochMs)
                )
                Log.d("SyncDebug", "✅ Volley: Pull Settings Sukses")
            }
        } catch (e: Exception) { Log.e("SyncDebug", "❌ Gagal pull Settings: ${e.message}") }

        Log.d("SyncDebug", "=== PULL SELESAI ===")
    }

    private suspend fun pushUsers(firestore: FirestoreManager) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM users WHERE isSynced = 0", null)
        val payloads = mutableListOf<UserSyncPayload>()
        val ids = mutableListOf<Long>()
        while (cursor.moveToNext()) {
            val user = cursor.toUser()
            ids.add(user.id)
            payloads.add(user.toSyncPayload())
            firestore.syncUser(user)
        }
        cursor.close()
        if (payloads.isNotEmpty()) {
            try {
                VolleyHelper.requestObject(context, Request.Method.POST, "sync/users", payloads)
                ids.forEach { dbHelper.writableDatabase.execSQL("UPDATE users SET isSynced = 1 WHERE id = ?", arrayOf(it)) }
            } catch (e: Exception) { Log.e("SyncDebug", "Push Users Error: ${e.message}") }
        }
    }

    private suspend fun pushMembers(firestore: FirestoreManager) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM members WHERE isSynced = 0", null)
        val payloads = mutableListOf<MemberSyncPayload>()
        val ids = mutableListOf<Long>()
        while (cursor.moveToNext()) {
            val m = cursor.toMember()
            ids.add(m.id)
            payloads.add(MemberSyncPayload(m.id, m.memberNo, m.name, m.phone, m.address, if (m.isActive) 1 else 0, m.createdAtEpochMs))
            firestore.syncMember(m)
        }
        cursor.close()
        if (payloads.isNotEmpty()) {
            try {
                VolleyHelper.requestObject(context, Request.Method.POST, "sync/members", payloads)
                ids.forEach { dbHelper.writableDatabase.execSQL("UPDATE members SET isSynced = 1 WHERE id = ?", arrayOf(it)) }
            } catch (e: Exception) { Log.e("SyncDebug", "Push Members Error: ${e.message}") }
        }
    }

    private suspend fun pushCategories(firestore: FirestoreManager) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM categories WHERE isSynced = 0", null)
        val payloads = mutableListOf<CategorySyncPayload>()
        val ids = mutableListOf<Long>()
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            val time = cursor.getLong(cursor.getColumnIndexOrThrow("createdAtEpochMs"))
            ids.add(id); payloads.add(CategorySyncPayload(name, time))
            firestore.syncCategory(CategoryEntity(id, name, time))
        }
        cursor.close()
        if (payloads.isNotEmpty()) {
            try {
                VolleyHelper.requestObject(context, Request.Method.POST, "sync/categories", payloads)
                ids.forEach { dbHelper.writableDatabase.execSQL("UPDATE categories SET isSynced = 1 WHERE id = ?", arrayOf(it)) }
            } catch (e: Exception) { Log.e("SyncDebug", "Push Categories Error: ${e.message}") }
        }
    }

    private suspend fun pushProducts(firestore: FirestoreManager) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM products WHERE isSynced = 0", null)
        val payloads = mutableListOf<ProductSyncPayload>()
        val ids = mutableListOf<Long>()
        while (cursor.moveToNext()) {
            val p = cursor.toProduct()
            ids.add(p.id)
            
            var base64: String? = null
            if (p.imagePath != null && p.imagePath.startsWith("/")) {
                try {
                    val file = java.io.File(p.imagePath)
                    if (file.exists()) {
                        val bytes = file.readBytes()
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            val outputStream = java.io.ByteArrayOutputStream()
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
                            base64 = android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
                        } else {
                            base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            payloads.add(ProductSyncPayload(p.id, p.barcode, p.name, p.category, p.price, p.stock, p.minimumStock, p.expiredDateEpochMs, p.imagePath, p.purchasePrice, p.createdAtEpochMs, base64))
            firestore.syncProduct(p)
        }
        cursor.close()
        if (payloads.isNotEmpty()) {
            try {
                VolleyHelper.requestObject(context, Request.Method.POST, "sync/products", payloads)
                ids.forEach { dbHelper.writableDatabase.execSQL("UPDATE products SET isSynced = 1 WHERE id = ?", arrayOf(it)) }
            } catch (e: Exception) { Log.e("SyncDebug", "Push Products Error: ${e.message}") }
        }
    }

    private suspend fun pushStockMovements(firestore: FirestoreManager) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM stock_movements WHERE isSynced = 0", null)
        val payloads = mutableListOf<StockMovementSyncPayload>()
        val ids = mutableListOf<Long>()
        while (cursor.moveToNext()) {
            val sm = cursor.toStockMovement()
            ids.add(sm.id)
            payloads.add(StockMovementSyncPayload(sm.id, sm.productId, sm.userId, sm.type, sm.quantityDelta, sm.note, sm.createdAtEpochMs))
            firestore.syncStockMovement(sm)
        }
        cursor.close()
        if (payloads.isNotEmpty()) {
            try {
                VolleyHelper.requestObject(context, Request.Method.POST, "sync/movements", payloads)
                ids.forEach { dbHelper.writableDatabase.execSQL("UPDATE stock_movements SET isSynced = 1 WHERE id = ?", arrayOf(it)) }
            } catch (e: Exception) { Log.e("SyncDebug", "Push Stock Error: ${e.message}") }
        }
    }

    private suspend fun pushSales(firestore: FirestoreManager) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM sales WHERE isSynced = 0", null)
        val payloads = mutableListOf<SaleSyncPayload>()
        val ids = mutableListOf<Long>()
        while (cursor.moveToNext()) {
            val sale = cursor.toSale()
            val items = mutableListOf<SaleItemEntity>()
            db.rawQuery("SELECT * FROM sale_items WHERE saleId = ?", arrayOf(sale.id.toString())).use { ic ->
                while (ic.moveToNext()) items.add(ic.toSaleItem())
            }
            ids.add(sale.id)
            payloads.add(SaleSyncPayload(sale.id, sale.transactionId, sale.cashierId, sale.subtotal, sale.discount, sale.tax, sale.total, sale.paymentMethod, sale.status, sale.createdAtEpochMs, items.map { SaleItemSyncPayload(it.id, it.productId, it.productName, it.unitPrice, it.quantity, it.lineTotal) }))
            firestore.syncSale(sale, items)
        }
        cursor.close()
        if (payloads.isNotEmpty()) {
            try {
                VolleyHelper.requestObject(context, Request.Method.POST, "sync/sales", payloads)
                ids.forEach { dbHelper.writableDatabase.execSQL("UPDATE sales SET isSynced = 1 WHERE id = ?", arrayOf(it)) }
            } catch (e: Exception) { Log.e("SyncDebug", "Push Sales Error: ${e.message}") }
        }
    }

    private suspend fun pushAuditLogs(firestore: FirestoreManager) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM audit_logs WHERE isSynced = 0", null)
        val payloads = mutableListOf<AuditLogSyncPayload>()
        val ids = mutableListOf<Long>()
        while (cursor.moveToNext()) {
            val log = cursor.toAudit()
            ids.add(log.id)
            payloads.add(AuditLogSyncPayload(log.id, log.userId, log.action, log.entity, log.entityId, log.detail, log.createdAtEpochMs))
            firestore.syncAuditLog(log)
        }
        cursor.close()
        if (payloads.isNotEmpty()) {
            try {
                VolleyHelper.requestObject(context, Request.Method.POST, "sync/audit", payloads)
                ids.forEach { dbHelper.writableDatabase.execSQL("UPDATE audit_logs SET isSynced = 1 WHERE id = ?", arrayOf(it)) }
            } catch (e: Exception) { Log.e("SyncDebug", "Push Audit Error: ${e.message}") }
        }
    }

    private suspend fun pushPromos(firestore: FirestoreManager) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM promos WHERE isSynced = 0", null)
        val payloads = mutableListOf<PromoSyncPayload>()
        val ids = mutableListOf<Long>()
        while (cursor.moveToNext()) {
            val p = cursor.toPromo()
            ids.add(p.id)
            payloads.add(PromoSyncPayload(p.id, p.code, p.name, p.description, p.discountPercent, p.validUntilEpochMs, if (p.isActive) 1 else 0))
            firestore.syncPromo(p)
        }
        cursor.close()
        if (payloads.isNotEmpty()) {
            try {
                VolleyHelper.requestObject(context, Request.Method.POST, "sync/promos", payloads)
                ids.forEach { dbHelper.writableDatabase.execSQL("UPDATE promos SET isSynced = 1 WHERE id = ?", arrayOf(it)) }
            } catch (e: Exception) { Log.e("SyncDebug", "Push Promos Error: ${e.message}") }
        }
    }

    private suspend fun pushSettings(firestore: FirestoreManager) {
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT * FROM settings LIMIT 1", null).use { cursor ->
            if (cursor.moveToFirst()) {
                val s = cursor.toSettings()
                firestore.syncSettings(s)
                if (cursor.getInt(cursor.getColumnIndexOrThrow("isSynced")) == 0) {
                    try {
                        VolleyHelper.requestObject(context, Request.Method.POST, "sync/settings", SettingsSyncPayload(s.koperasiName, s.koperasiAddress, s.koperasiPhone, s.taxPercent, s.discountPercent, s.shuParameter, s.latitude, s.longitude, s.updatedAtEpochMs))
                        dbHelper.writableDatabase.execSQL("UPDATE settings SET isSynced = 1 WHERE id = 1")
                    } catch (e: Exception) { Log.e("SyncDebug", "Push Settings Error: ${e.message}") }
                }
            }
        }
    }

    private fun android.database.Cursor.toUser() = UserEntity(getLong(getColumnIndexOrThrow("id")), getString(getColumnIndexOrThrow("name")), getString(getColumnIndexOrThrow("username")), getString(getColumnIndexOrThrow("passwordHash")), getString(getColumnIndexOrThrow("salt")), Role.valueOf(getString(getColumnIndexOrThrow("role"))), getInt(getColumnIndexOrThrow("isActive")) == 1, getInt(getColumnIndexOrThrow("needsPasswordReset")) == 1, getInt(getColumnIndexOrThrow("isSynced")) == 1, getLong(getColumnIndexOrThrow("createdAtEpochMs")))
    private fun android.database.Cursor.toMember() = MemberEntity(getLong(getColumnIndexOrThrow("id")), getString(getColumnIndexOrThrow("memberNo")), getString(getColumnIndexOrThrow("name")), getString(getColumnIndexOrThrow("phone")), getString(getColumnIndexOrThrow("address")), getInt(getColumnIndexOrThrow("isActive")) == 1, getInt(getColumnIndexOrThrow("isSynced")) == 1, getLong(getColumnIndexOrThrow("createdAtEpochMs")))
    private fun android.database.Cursor.toProduct() = ProductEntity(getLong(getColumnIndexOrThrow("id")), getString(getColumnIndexOrThrow("barcode")), getString(getColumnIndexOrThrow("name")), getString(getColumnIndexOrThrow("category")), getLong(getColumnIndexOrThrow("price")), getLong(getColumnIndexOrThrow("purchasePrice")), getLong(getColumnIndexOrThrow("stock")), getLong(getColumnIndexOrThrow("minimumStock")), if (isNull(getColumnIndexOrThrow("expiredDateEpochMs"))) null else getLong(getColumnIndexOrThrow("expiredDateEpochMs")), getString(getColumnIndexOrThrow("imagePath")), getInt(getColumnIndexOrThrow("isSynced")) == 1, getLong(getColumnIndexOrThrow("createdAtEpochMs")))
    private fun android.database.Cursor.toStockMovement() = StockMovementEntity(getLong(getColumnIndexOrThrow("id")), getLong(getColumnIndexOrThrow("productId")), if (isNull(getColumnIndexOrThrow("userId"))) null else getLong(getColumnIndexOrThrow("userId")), getString(getColumnIndexOrThrow("type")), getLong(getColumnIndexOrThrow("quantityDelta")), getString(getColumnIndexOrThrow("note")), getInt(getColumnIndexOrThrow("isSynced")) == 1, getLong(getColumnIndexOrThrow("createdAtEpochMs")))
    private fun android.database.Cursor.toSale() = SaleEntity(getLong(getColumnIndexOrThrow("id")), getString(getColumnIndexOrThrow("transactionId")), if (isNull(getColumnIndexOrThrow("cashierId"))) null else getLong(getColumnIndexOrThrow("cashierId")), getLong(getColumnIndexOrThrow("subtotal")), getLong(getColumnIndexOrThrow("discount")), getLong(getColumnIndexOrThrow("tax")), getLong(getColumnIndexOrThrow("total")), getString(getColumnIndexOrThrow("paymentMethod")), getString(getColumnIndexOrThrow("status")), getInt(getColumnIndexOrThrow("isSynced")) == 1, getLong(getColumnIndexOrThrow("createdAtEpochMs")))
    private fun android.database.Cursor.toSaleItem() = SaleItemEntity(getLong(getColumnIndexOrThrow("id")), getLong(getColumnIndexOrThrow("saleId")), if (isNull(getColumnIndexOrThrow("productId"))) null else getLong(getColumnIndexOrThrow("productId")), getString(getColumnIndexOrThrow("productName")), getLong(getColumnIndexOrThrow("unitPrice")), getLong(getColumnIndexOrThrow("quantity")), getLong(getColumnIndexOrThrow("lineTotal")))
    private fun android.database.Cursor.toAudit() = AuditLogEntity(getLong(getColumnIndexOrThrow("id")), if (isNull(getColumnIndexOrThrow("userId"))) null else getLong(getColumnIndexOrThrow("userId")), getString(getColumnIndexOrThrow("action")), getString(getColumnIndexOrThrow("entity")), if (isNull(getColumnIndexOrThrow("entityId"))) null else getLong(getColumnIndexOrThrow("entityId")), getString(getColumnIndexOrThrow("detail")), getInt(getColumnIndexOrThrow("isSynced")) == 1, getLong(getColumnIndexOrThrow("createdAtEpochMs")))
    private fun android.database.Cursor.toPromo() = PromoEntity(getLong(getColumnIndexOrThrow("id")), getString(getColumnIndexOrThrow("code")), getString(getColumnIndexOrThrow("name")), getString(getColumnIndexOrThrow("description")), getDouble(getColumnIndexOrThrow("discountPercent")), getLong(getColumnIndexOrThrow("validUntilEpochMs")), getString(getColumnIndexOrThrow("promoType")) ?: "TRANSACTION", getLong(getColumnIndexOrThrow("minimumPurchase")), if (isNull(getColumnIndexOrThrow("productId"))) null else getLong(getColumnIndexOrThrow("productId")), getInt(getColumnIndexOrThrow("isSynced")) == 1, getInt(getColumnIndexOrThrow("isActive")) == 1)
    private fun android.database.Cursor.toSettings() = SettingsEntity(1, getString(getColumnIndexOrThrow("koperasiName")), getString(getColumnIndexOrThrow("koperasiAddress")), getString(getColumnIndexOrThrow("koperasiPhone")), null, getDouble(getColumnIndexOrThrow("taxPercent")), getDouble(getColumnIndexOrThrow("discountPercent")), getDouble(getColumnIndexOrThrow("shuParameter")), if (isNull(getColumnIndexOrThrow("latitude"))) null else getDouble(getColumnIndexOrThrow("latitude")), if (isNull(getColumnIndexOrThrow("longitude"))) null else getDouble(getColumnIndexOrThrow("longitude")), getInt(getColumnIndexOrThrow("isSynced")) == 1, getLong(getColumnIndexOrThrow("updatedAtEpochMs")))
    private fun UserEntity.toSyncPayload() = UserSyncPayload(id, name, username, passwordHash, salt, role.name, if (isActive) 1 else 0, if (needsPasswordReset) 1 else 0, createdAtEpochMs)
}
