package com.kopdes.kopdesjajar.data.network

import android.content.Context
import android.util.Log
import com.kopdes.kopdesjajar.data.db.*
import com.kopdes.kopdesjajar.data.firebase.FirestoreManager
import com.kopdes.kopdesjajar.data.model.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

class SyncManager(context: Context) {
    private val dbHelper = KoperasiDbHelper(context)
    private val api = RetrofitClient.instance

    suspend fun pushAllDataToServer() = withContext(Dispatchers.IO) {
        Log.d("SyncDebug", "=== MEMULAI SYNC KE LARAVEL & FIREBASE ===")
        val firestore = FirestoreManager()
        
        // Jalankan seluruh push secara parallel (concurrent) untuk kecepatan maksimal
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
        Log.d("SyncDebug", "=== MEMULAI PULL DATA DARI SERVER & CLOUD ===")
        val firestore = FirestoreManager()
        val db = dbHelper.writableDatabase

        // 1. Restore Users
        var usersRestored = false
        try {
            Log.d("SyncDebug", "🔄 Mencoba pull Users dari Laravel MySQL...")
            val usersResp = api.pullUsers()
            if (usersResp.isSuccessful) {
                val users = usersResp.body() ?: emptyList()
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
                            arrayOf(u.id, u.name, u.username, u.passwordHash, u.salt, u.role, if (u.isActive == 1) 1 else 0, if (u.needsPasswordReset == 1) 1 else 0, u.createdAtEpochMs)
                        )
                    }
                    db.setTransactionSuccessful()
                    Log.d("SyncDebug", "✅ Pull Users dari Laravel MySQL Sukses (${users.size} data)")
                    usersRestored = true
                } finally {
                    db.endTransaction()
                }
            }
        } catch (e: Exception) {
            Log.e("SyncDebug", "❌ Gagal pull Users dari Laravel MySQL: ${e.message}")
        }
        if (!usersRestored) {
            try {
                Log.d("SyncDebug", "🔄 Fallback: Pull Users dari Firebase Firestore...")
                val users = firestore.getAllUsers()
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
                            arrayOf(u.id, u.name, u.username, u.passwordHash, u.salt, u.role.name, if (u.isActive) 1 else 0, if (u.needsPasswordReset) 1 else 0, u.createdAtEpochMs)
                        )
                    }
                    db.setTransactionSuccessful()
                    Log.d("SyncDebug", "✅ Fallback Pull Users dari Firebase Firestore Sukses (${users.size} data)")
                } finally {
                    db.endTransaction()
                }
            } catch (e: Exception) {
                Log.e("SyncDebug", "❌ Gagal fallback pull Users dari Firebase Firestore: ${e.message}")
            }
        }

        // 2. Restore Products
        var productsRestored = false
        try {
            Log.d("SyncDebug", "🔄 Mencoba pull Products dari Laravel MySQL...")
            val productsResp = api.pullProducts()
            if (productsResp.isSuccessful) {
                val products = productsResp.body() ?: emptyList()
                db.beginTransaction()
                try {
                    products.forEach { p ->
                        val queryCursor = db.rawQuery("SELECT id FROM products WHERE (barcode IS NOT NULL AND barcode = ?) OR name = ? LIMIT 1", arrayOf(p.barcode ?: "", p.name))
                        val exists = queryCursor.moveToFirst()
                        if (exists) {
                            val localId = queryCursor.getLong(0)
                            db.execSQL(
                                """
                                UPDATE products SET 
                                    id = ?, barcode = ?, name = ?, category = ?, price = ?, stock = ?, 
                                    purchasePrice = ?, minimumStock = ?, expiredDateEpochMs = ?, 
                                    imagePath = ?, isSynced = 1
                                WHERE id = ?
                                """.trimIndent(),
                                arrayOf(p.id, p.barcode, p.name, p.category, p.price, p.stock, p.purchasePrice, p.minimumStock, p.expiredDateEpochMs, p.imagePath, localId)
                            )
                        } else {
                            db.execSQL(
                                """
                                INSERT INTO products (id, barcode, name, category, price, stock, purchasePrice, minimumStock, expiredDateEpochMs, imagePath, isSynced, createdAtEpochMs)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                                """.trimIndent(),
                                arrayOf(p.id, p.barcode, p.name, p.category, p.price, p.stock, p.purchasePrice, p.minimumStock, p.expiredDateEpochMs, p.imagePath, p.createdAtEpochMs)
                            )
                        }
                        queryCursor.close()
                    }
                    db.setTransactionSuccessful()
                    Log.d("SyncDebug", "✅ Pull Products dari Laravel MySQL Sukses (${products.size} data)")
                    productsRestored = true
                } finally {
                    db.endTransaction()
                }
            }
        } catch (e: Exception) {
            Log.e("SyncDebug", "❌ Gagal pull Products dari Laravel MySQL: ${e.message}")
        }
        if (!productsRestored) {
            try {
                Log.d("SyncDebug", "🔄 Fallback: Pull Products dari Firebase Firestore...")
                val products = firestore.getAllProducts()
                db.beginTransaction()
                try {
                    products.forEach { p ->
                        val queryCursor = db.rawQuery("SELECT id FROM products WHERE (barcode IS NOT NULL AND barcode = ?) OR name = ? LIMIT 1", arrayOf(p.barcode ?: "", p.name))
                        val exists = queryCursor.moveToFirst()
                        if (exists) {
                            val localId = queryCursor.getLong(0)
                            db.execSQL(
                                """
                                UPDATE products SET 
                                    id = ?, barcode = ?, name = ?, category = ?, price = ?, stock = ?, 
                                    purchasePrice = ?, minimumStock = ?, expiredDateEpochMs = ?, 
                                    imagePath = ?, isSynced = 1
                                WHERE id = ?
                                """.trimIndent(),
                                arrayOf(p.id, p.barcode, p.name, p.category, p.price, p.stock, p.purchasePrice, p.minimumStock, p.expiredDateEpochMs, p.imagePath, localId)
                            )
                        } else {
                            db.execSQL(
                                """
                                INSERT INTO products (id, barcode, name, category, price, stock, purchasePrice, minimumStock, expiredDateEpochMs, imagePath, isSynced, createdAtEpochMs)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                                """.trimIndent(),
                                arrayOf(p.id, p.barcode, p.name, p.category, p.price, p.stock, p.purchasePrice, p.minimumStock, p.expiredDateEpochMs, p.imagePath, p.createdAtEpochMs)
                            )
                        }
                        queryCursor.close()
                    }
                    db.setTransactionSuccessful()
                    Log.d("SyncDebug", "✅ Fallback Pull Products dari Firebase Firestore Sukses (${products.size} data)")
                } finally {
                    db.endTransaction()
                }
            } catch (e: Exception) {
                Log.e("SyncDebug", "❌ Gagal fallback pull Products dari Firebase Firestore: ${e.message}")
            }
        }

        // 3. Restore Categories
        try {
            Log.d("SyncDebug", "🔄 Pull Categories dari Firebase Firestore...")
            val categories = firestore.getAllCategories()
            db.beginTransaction()
            try {
                categories.forEach { cat ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO categories (id, name, createdAtEpochMs, isSynced) VALUES (?, ?, ?, 1)",
                        arrayOf(cat.id, cat.name, cat.createdAtEpochMs)
                    )
                }
                db.setTransactionSuccessful()
                Log.d("SyncDebug", "✅ Pull Categories Sukses (${categories.size} data)")
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.e("SyncDebug", "❌ Error pull categories: ${e.message}")
        }

        // 4. Restore Members
        var membersRestored = false
        try {
            Log.d("SyncDebug", "🔄 Mencoba pull Members dari Laravel MySQL...")
            val membersResp = api.pullMembers()
            if (membersResp.isSuccessful) {
                val members = membersResp.body() ?: emptyList()
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
                            arrayOf(m.id, m.memberNo, m.name, m.phone, m.address, if (m.isActive == 1) 1 else 0, m.createdAtEpochMs)
                        )
                    }
                    db.setTransactionSuccessful()
                    Log.d("SyncDebug", "✅ Pull Members dari Laravel MySQL Sukses (${members.size} data)")
                    membersRestored = true
                } finally {
                    db.endTransaction()
                }
            }
        } catch (e: Exception) {
            Log.e("SyncDebug", "❌ Gagal pull Members dari Laravel MySQL: ${e.message}")
        }
        if (!membersRestored) {
            try {
                Log.d("SyncDebug", "🔄 Fallback: Pull Members dari Firebase Firestore...")
                val members = firestore.getAllMembers()
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
                            arrayOf(m.id, m.memberNo, m.name, m.phone, m.address, if (m.isActive) 1 else 0, m.createdAtEpochMs)
                        )
                    }
                    db.setTransactionSuccessful()
                    Log.d("SyncDebug", "✅ Fallback Pull Members dari Firebase Firestore Sukses (${members.size} data)")
                } finally {
                    db.endTransaction()
                }
            } catch (e: Exception) {
                Log.e("SyncDebug", "❌ Gagal fallback pull Members dari Firebase Firestore: ${e.message}")
            }
        }

        // 5. Restore Promos
        var promosRestored = false
        try {
            Log.d("SyncDebug", "🔄 Mencoba pull Promos dari Laravel MySQL...")
            val promosResp = api.pullPromos()
            if (promosResp.isSuccessful) {
                val promos = promosResp.body() ?: emptyList()
                db.beginTransaction()
                try {
                    promos.forEach { pr ->
                        val queryCursor = db.rawQuery("SELECT id FROM promos WHERE code = ? LIMIT 1", arrayOf(pr.code))
                        val exists = queryCursor.moveToFirst()
                        if (exists) {
                            val localId = queryCursor.getLong(0)
                            db.execSQL(
                                "UPDATE promos SET id = ?, name = ?, discountPercent = ?, validUntilEpochMs = ?, isActive = ?, isSynced = 1 WHERE id = ?",
                                arrayOf(pr.id, pr.name, pr.discountPercent, pr.validUntilEpochMs, if (pr.isActive == 1) 1 else 0, localId)
                            )
                        } else {
                            db.execSQL(
                                "INSERT INTO promos (id, code, name, discountPercent, validUntilEpochMs, isActive, isSynced) VALUES (?, ?, ?, ?, ?, ?, 1)",
                                arrayOf(pr.id, pr.code, pr.name, pr.discountPercent, pr.validUntilEpochMs, if (pr.isActive == 1) 1 else 0)
                            )
                        }
                        queryCursor.close()
                    }
                    db.setTransactionSuccessful()
                    Log.d("SyncDebug", "✅ Pull Promos dari Laravel MySQL Sukses (${promos.size} data)")
                    promosRestored = true
                } finally {
                    db.endTransaction()
                }
            }
        } catch (e: Exception) {
            Log.e("SyncDebug", "❌ Gagal pull Promos dari Laravel MySQL: ${e.message}")
        }
        if (!promosRestored) {
            try {
                Log.d("SyncDebug", "🔄 Fallback: Pull Promos dari Firebase Firestore...")
                val promos = firestore.getAllPromos()
                db.beginTransaction()
                try {
                    promos.forEach { pr ->
                        val queryCursor = db.rawQuery("SELECT id FROM promos WHERE code = ? LIMIT 1", arrayOf(pr.code))
                        val exists = queryCursor.moveToFirst()
                        if (exists) {
                            val localId = queryCursor.getLong(0)
                            db.execSQL(
                                "UPDATE promos SET id = ?, name = ?, discountPercent = ?, validUntilEpochMs = ?, isActive = ?, isSynced = 1 WHERE id = ?",
                                arrayOf(pr.id, pr.name, pr.discountPercent, pr.validUntilEpochMs, if (pr.isActive) 1 else 0, localId)
                            )
                        } else {
                            db.execSQL(
                                "INSERT INTO promos (id, code, name, discountPercent, validUntilEpochMs, isActive, isSynced) VALUES (?, ?, ?, ?, ?, ?, 1)",
                                arrayOf(pr.id, pr.code, pr.name, pr.discountPercent, pr.validUntilEpochMs, if (pr.isActive) 1 else 0)
                            )
                        }
                        queryCursor.close()
                    }
                    db.setTransactionSuccessful()
                    Log.d("SyncDebug", "✅ Fallback Pull Promos dari Firebase Firestore Sukses (${promos.size} data)")
                } finally {
                    db.endTransaction()
                }
            } catch (e: Exception) {
                Log.e("SyncDebug", "❌ Gagal fallback pull Promos dari Firebase Firestore: ${e.message}")
            }
        }

        // 6. Restore Settings
        var settingsRestored = false
        try {
            Log.d("SyncDebug", "🔄 Mencoba pull Settings dari Laravel MySQL...")
            val settingsResp = api.pullSettings()
            if (settingsResp.isSuccessful) {
                val settingsList = settingsResp.body() ?: emptyList()
                if (settingsList.isNotEmpty()) {
                    val it = settingsList[0]
                    db.execSQL(
                        """
                        INSERT OR REPLACE INTO settings (id, koperasiName, koperasiAddress, koperasiPhone, taxPercent, discountPercent, shuParameter, latitude, longitude, isSynced, updatedAtEpochMs) 
                        VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                        """.trimIndent(),
                        arrayOf(it.koperasiName, it.koperasiAddress, it.koperasiPhone, it.taxPercent, it.discountPercent, it.shuParameter, it.latitude, it.longitude, it.updatedAtEpochMs)
                    )
                    Log.d("SyncDebug", "✅ Pull Settings dari Laravel MySQL Sukses")
                    settingsRestored = true
                }
            }
        } catch (e: Exception) {
            Log.e("SyncDebug", "❌ Gagal pull Settings dari Laravel MySQL: ${e.message}")
        }
        if (!settingsRestored) {
            try {
                Log.d("SyncDebug", "🔄 Fallback: Pull Settings dari Firebase Firestore...")
                val it = firestore.getRemoteSettings()
                it?.let {
                    db.execSQL(
                        """
                        INSERT OR REPLACE INTO settings (id, koperasiName, koperasiAddress, koperasiPhone, taxPercent, discountPercent, shuParameter, latitude, longitude, isSynced, updatedAtEpochMs) 
                        VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                        """.trimIndent(),
                        arrayOf(it.koperasiName, it.koperasiAddress, it.koperasiPhone, it.taxPercent, it.discountPercent, it.shuParameter, it.latitude, it.longitude, it.updatedAtEpochMs)
                    )
                    Log.d("SyncDebug", "✅ Fallback Pull Settings dari Firebase Firestore Sukses")
                }
            } catch (e: Exception) {
                Log.e("SyncDebug", "❌ Gagal fallback pull Settings dari Firebase Firestore: ${e.message}")
            }
        }

        // 7. Restore Sales
        var salesRestored = false
        try {
            Log.d("SyncDebug", "🔄 Mencoba pull Sales dari Laravel MySQL...")
            val salesResp = api.pullSales()
            if (salesResp.isSuccessful) {
                val sales = salesResp.body() ?: emptyList()
                db.beginTransaction()
                try {
                    sales.forEach { salePayload ->
                        val exists = db.rawQuery("SELECT id FROM sales WHERE transactionId = ?", arrayOf(salePayload.transactionId)).use { it.moveToFirst() }
                        if (!exists) {
                            val cv = android.content.ContentValues().apply {
                                put("id", salePayload.id)
                                put("transactionId", salePayload.transactionId)
                                put("cashierId", salePayload.cashierId)
                                put("subtotal", salePayload.subtotal)
                                put("discount", salePayload.discount)
                                put("tax", salePayload.tax)
                                put("total", salePayload.total)
                                put("paymentMethod", salePayload.paymentMethod)
                                put("status", salePayload.status)
                                put("isSynced", 1)
                                put("createdAtEpochMs", salePayload.createdAtEpochMs)
                            }
                            val saleId = db.insertOrThrow("sales", null, cv)
                            salePayload.items.forEach { item ->
                                val itemCv = android.content.ContentValues().apply {
                                    put("id", item.id)
                                    put("saleId", saleId)
                                    put("productId", item.productId)
                                    put("productName", item.productName)
                                    put("unitPrice", item.unitPrice)
                                    put("quantity", item.quantity)
                                    put("lineTotal", item.lineTotal)
                                }
                                db.insertOrThrow("sale_items", null, itemCv)
                            }
                        }
                    }
                    db.setTransactionSuccessful()
                    Log.d("SyncDebug", "✅ Pull Sales dari Laravel MySQL Sukses (${sales.size} data)")
                    salesRestored = true
                } finally {
                    db.endTransaction()
                }
            }
        } catch (e: Exception) {
            Log.e("SyncDebug", "❌ Gagal pull Sales dari Laravel MySQL: ${e.message}")
        }
        if (!salesRestored) {
            try {
                Log.d("SyncDebug", "🔄 Fallback: Pull Sales dari Firebase Firestore...")
                val sales = firestore.getAllSales()
                db.beginTransaction()
                try {
                    sales.forEach { (sale, items) ->
                        val exists = db.rawQuery("SELECT id FROM sales WHERE transactionId = ?", arrayOf(sale.transactionId)).use { it.moveToFirst() }
                        if (!exists) {
                            val cv = android.content.ContentValues().apply {
                                put("id", sale.id)
                                put("transactionId", sale.transactionId)
                                put("cashierId", sale.cashierId)
                                put("subtotal", sale.subtotal)
                                put("discount", sale.discount)
                                put("tax", sale.tax)
                                put("total", sale.total)
                                put("paymentMethod", sale.paymentMethod)
                                put("status", sale.status)
                                put("isSynced", 1)
                                put("createdAtEpochMs", sale.createdAtEpochMs)
                            }
                            val saleId = db.insertOrThrow("sales", null, cv)
                            items.forEach { item ->
                                val itemCv = android.content.ContentValues().apply {
                                    put("id", item.id)
                                    put("saleId", saleId)
                                    put("productId", item.productId)
                                    put("productName", item.productName)
                                    put("unitPrice", item.unitPrice)
                                    put("quantity", item.quantity)
                                    put("lineTotal", item.lineTotal)
                                }
                                db.insertOrThrow("sale_items", null, itemCv)
                            }
                        }
                    }
                    db.setTransactionSuccessful()
                    Log.d("SyncDebug", "✅ Fallback Pull Sales dari Firebase Firestore Sukses (${sales.size} data)")
                } finally {
                    db.endTransaction()
                }
            } catch (e: Exception) {
                Log.e("SyncDebug", "❌ Gagal fallback pull Sales dari Firebase Firestore: ${e.message}")
            }
        }

        // 8. Restore Stock Movements
        var movementsRestored = false
        try {
            Log.d("SyncDebug", "🔄 Mencoba pull Stock Movements dari Laravel MySQL...")
            val movementsResp = api.pullStockMovements()
            if (movementsResp.isSuccessful) {
                val movements = movementsResp.body() ?: emptyList()
                db.beginTransaction()
                try {
                    movements.forEach { sm ->
                        val exists = db.rawQuery("SELECT id FROM stock_movements WHERE id = ?", arrayOf(sm.id.toString())).use { it.moveToFirst() }
                        if (!exists) {
                            val cv = android.content.ContentValues().apply {
                                put("id", sm.id)
                                put("productId", sm.productId)
                                put("userId", sm.userId)
                                put("type", sm.type)
                                put("quantityDelta", sm.quantityDelta)
                                put("note", sm.note)
                                put("isSynced", 1)
                                put("createdAtEpochMs", sm.createdAtEpochMs)
                            }
                            db.insertOrThrow("stock_movements", null, cv)
                        }
                    }
                    db.setTransactionSuccessful()
                    Log.d("SyncDebug", "✅ Pull Stock Movements dari Laravel MySQL Sukses (${movements.size} data)")
                    movementsRestored = true
                } finally {
                    db.endTransaction()
                }
            }
        } catch (e: Exception) {
            Log.e("SyncDebug", "❌ Gagal pull Stock Movements dari Laravel MySQL: ${e.message}")
        }
        if (!movementsRestored) {
            try {
                Log.d("SyncDebug", "🔄 Fallback: Pull Stock Movements dari Firebase Firestore...")
                val movements = firestore.getAllStockMovements()
                db.beginTransaction()
                try {
                    movements.forEach { sm ->
                        val exists = db.rawQuery("SELECT id FROM stock_movements WHERE id = ?", arrayOf(sm.id.toString())).use { it.moveToFirst() }
                        if (!exists) {
                            val cv = android.content.ContentValues().apply {
                                put("id", sm.id)
                                put("productId", sm.productId)
                                put("userId", sm.userId)
                                put("type", sm.type)
                                put("quantityDelta", sm.quantityDelta)
                                put("note", sm.note)
                                put("isSynced", 1)
                                put("createdAtEpochMs", sm.createdAtEpochMs)
                            }
                            db.insertOrThrow("stock_movements", null, cv)
                        }
                    }
                    db.setTransactionSuccessful()
                    Log.d("SyncDebug", "✅ Fallback Pull Stock Movements dari Firebase Firestore Sukses (${movements.size} data)")
                } finally {
                    db.endTransaction()
                }
            } catch (e: Exception) {
                Log.e("SyncDebug", "❌ Gagal fallback pull Stock Movements dari Firebase Firestore: ${e.message}")
            }
        }

        // 9. Restore Audit Logs
        var auditLogsRestored = false
        try {
            Log.d("SyncDebug", "🔄 Mencoba pull Audit Logs dari Laravel MySQL...")
            val auditResp = api.pullAuditLogs()
            if (auditResp.isSuccessful) {
                val auditLogs = auditResp.body() ?: emptyList()
                db.beginTransaction()
                try {
                    auditLogs.forEach { log ->
                        val exists = db.rawQuery("SELECT id FROM audit_logs WHERE id = ?", arrayOf(log.id.toString())).use { it.moveToFirst() }
                        if (!exists) {
                            val cv = android.content.ContentValues().apply {
                                put("id", log.id)
                                put("userId", log.userId)
                                put("action", log.action)
                                put("entity", log.entity)
                                put("entityId", log.entityId)
                                put("detail", log.detail)
                                put("isSynced", 1)
                                put("createdAtEpochMs", log.createdAtEpochMs)
                            }
                            db.insertOrThrow("audit_logs", null, cv)
                        }
                    }
                    db.setTransactionSuccessful()
                    Log.d("SyncDebug", "✅ Pull Audit Logs dari Laravel MySQL Sukses (${auditLogs.size} data)")
                    auditLogsRestored = true
                } finally {
                    db.endTransaction()
                }
            }
        } catch (e: Exception) {
            Log.e("SyncDebug", "❌ Gagal pull Audit Logs dari Laravel MySQL: ${e.message}")
        }
        if (!auditLogsRestored) {
            try {
                Log.d("SyncDebug", "🔄 Fallback: Pull Audit Logs dari Firebase Firestore...")
                val auditLogs = firestore.getAllAuditLogs()
                db.beginTransaction()
                try {
                    auditLogs.forEach { log ->
                        val exists = db.rawQuery("SELECT id FROM audit_logs WHERE id = ?", arrayOf(log.id.toString())).use { it.moveToFirst() }
                        if (!exists) {
                            val cv = android.content.ContentValues().apply {
                                put("id", log.id)
                                put("userId", log.userId)
                                put("action", log.action)
                                put("entity", log.entity)
                                put("entityId", log.entityId)
                                put("detail", log.detail)
                                put("isSynced", 1)
                                put("createdAtEpochMs", log.createdAtEpochMs)
                            }
                            db.insertOrThrow("audit_logs", null, cv)
                        }
                    }
                    db.setTransactionSuccessful()
                    Log.d("SyncDebug", "✅ Fallback Pull Audit Logs dari Firebase Firestore Sukses (${auditLogs.size} data)")
                } finally {
                    db.endTransaction()
                }
            } catch (e: Exception) {
                Log.e("SyncDebug", "❌ Gagal fallback pull Audit Logs dari Firebase Firestore: ${e.message}")
            }
        }

        Log.d("SyncDebug", "=== PULL SELESAI ===")
    }

    private suspend fun pushUsers(firestore: FirestoreManager) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM users WHERE isSynced = 0", null)
        val payloads = mutableListOf<UserSyncPayload>()
        val usersList = mutableListOf<UserEntity>()
        while (cursor.moveToNext()) {
            val user = cursor.toUser()
            usersList.add(user)
            payloads.add(user.toSyncPayload())
        }
        cursor.close()

        if (payloads.isNotEmpty()) {
            try {
                usersList.forEach { user -> firestore.syncUser(user) }
                Log.d("SyncDebug", "✅ Firestore: Pushed ${usersList.size} users!")
            } catch (e: Exception) {
                Log.e("SyncDebug", "❌ Firestore: Gagal push users: ${e.message}")
            }

            try {
                val response = api.syncUsers(payloads)
                if (response.isSuccessful) {
                    Log.d("SyncDebug", "✅ Laravel: Semua user masuk ke MySQL!")
                    val writeDb = dbHelper.writableDatabase
                    writeDb.beginTransaction()
                    try {
                        usersList.forEach { user ->
                            writeDb.execSQL("UPDATE users SET isSynced = 1 WHERE id = ?", arrayOf(user.id))
                        }
                        writeDb.setTransactionSuccessful()
                    } finally {
                        writeDb.endTransaction()
                    }
                }
            } catch (e: Exception) { Log.e("SyncDebug", "❌ Laravel: Gagal push users: ${e.message}") }
        }
    }

    private suspend fun pushMembers(firestore: FirestoreManager) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM members WHERE isSynced = 0", null)
        val payloads = mutableListOf<MemberSyncPayload>()
        val membersList = mutableListOf<MemberEntity>()
        while (cursor.moveToNext()) {
            val member = cursor.toMember()
            membersList.add(member)
            payloads.add(MemberSyncPayload(member.id, member.memberNo, member.name, member.phone, member.address, if (member.isActive) 1 else 0, member.createdAtEpochMs))
        }
        cursor.close()

        if (payloads.isNotEmpty()) {
            try {
                membersList.forEach { member -> firestore.syncMember(member) }
                Log.d("SyncDebug", "✅ Firestore: Pushed ${membersList.size} members!")
            } catch (e: Exception) {
                Log.e("SyncDebug", "❌ Firestore: Gagal push members: ${e.message}")
            }

            try {
                val response = api.syncMembers(payloads)
                if (response.isSuccessful) {
                    Log.d("SyncDebug", "✅ Laravel: Semua member masuk ke MySQL!")
                    val writeDb = dbHelper.writableDatabase
                    writeDb.beginTransaction()
                    try {
                        membersList.forEach { member ->
                            writeDb.execSQL("UPDATE members SET isSynced = 1 WHERE id = ?", arrayOf(member.id))
                        }
                        writeDb.setTransactionSuccessful()
                    } finally {
                        writeDb.endTransaction()
                    }
                }
            } catch (e: Exception) { Log.e("SyncDebug", "❌ Laravel: Gagal push members: ${e.message}") }
        }
    }

    private suspend fun pushCategories(firestore: FirestoreManager) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM categories WHERE isSynced = 0", null)
        val payloads = mutableListOf<CategorySyncPayload>()
        val categoriesList = mutableListOf<CategoryEntity>()
        while (cursor.moveToNext()) {
            val cat = CategoryEntity(cursor.getLong(cursor.getColumnIndexOrThrow("id")), cursor.getString(cursor.getColumnIndexOrThrow("name")), cursor.getLong(cursor.getColumnIndexOrThrow("createdAtEpochMs")))
            categoriesList.add(cat)
            payloads.add(CategorySyncPayload(cat.name, cat.createdAtEpochMs))
        }
        cursor.close()

        if (payloads.isNotEmpty()) {
            try {
                categoriesList.forEach { cat -> firestore.syncCategory(cat) }
                Log.d("SyncDebug", "✅ Firestore: Pushed ${categoriesList.size} categories!")
            } catch (e: Exception) {
                Log.e("SyncDebug", "❌ Firestore: Gagal push categories: ${e.message}")
            }

            try {
                val response = api.syncCategories(payloads)
                if (response.isSuccessful) {
                    Log.d("SyncDebug", "✅ Laravel: Semua kategori masuk ke MySQL!")
                    val writeDb = dbHelper.writableDatabase
                    writeDb.beginTransaction()
                    try {
                        categoriesList.forEach { cat ->
                            writeDb.execSQL("UPDATE categories SET isSynced = 1 WHERE id = ?", arrayOf(cat.id))
                        }
                        writeDb.setTransactionSuccessful()
                    } finally {
                        writeDb.endTransaction()
                    }
                }
            } catch (e: Exception) { Log.e("SyncDebug", "❌ Laravel: Gagal push categories: ${e.message}") }
        }
    }

    private suspend fun pushProducts(firestore: FirestoreManager) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM products WHERE isSynced = 0", null)
        val payloads = mutableListOf<ProductSyncPayload>()
        val productsList = mutableListOf<ProductEntity>()
        while (cursor.moveToNext()) {
            val p = cursor.toProduct()
            productsList.add(p)
            payloads.add(ProductSyncPayload(p.id, p.barcode, p.name, p.category, p.price, p.stock, p.minimumStock, p.expiredDateEpochMs, p.imagePath, p.purchasePrice, p.createdAtEpochMs))
        }
        cursor.close()

        if (payloads.isNotEmpty()) {
            try {
                productsList.forEach { p -> firestore.syncProduct(p) }
                Log.d("SyncDebug", "✅ Firestore: Pushed ${productsList.size} products!")
            } catch (e: Exception) {
                Log.e("SyncDebug", "❌ Firestore: Gagal push products: ${e.message}")
            }

            try {
                val response = api.syncProducts(payloads)
                if (response.isSuccessful) {
                    Log.d("SyncDebug", "✅ Laravel: Semua produk masuk ke MySQL!")
                    val writeDb = dbHelper.writableDatabase
                    writeDb.beginTransaction()
                    try {
                        productsList.forEach { p ->
                            writeDb.execSQL("UPDATE products SET isSynced = 1 WHERE id = ?", arrayOf(p.id))
                        }
                        writeDb.setTransactionSuccessful()
                    } finally {
                        writeDb.endTransaction()
                    }
                }
            } catch (e: Exception) { Log.e("SyncDebug", "❌ Laravel: Gagal push products: ${e.message}") }
        }
    }

    private suspend fun pushStockMovements(firestore: FirestoreManager) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM stock_movements WHERE isSynced = 0", null)
        val payloads = mutableListOf<StockMovementSyncPayload>()
        val movementsList = mutableListOf<StockMovementEntity>()
        while (cursor.moveToNext()) {
            val sm = cursor.toStockMovement()
            movementsList.add(sm)
            payloads.add(StockMovementSyncPayload(sm.id, sm.productId, sm.userId, sm.type, sm.quantityDelta, sm.note, sm.createdAtEpochMs))
        }
        cursor.close()

        if (payloads.isNotEmpty()) {
            try {
                movementsList.forEach { sm -> firestore.syncStockMovement(sm) }
                Log.d("SyncDebug", "✅ Firestore: Pushed ${movementsList.size} stock movements!")
            } catch (e: Exception) {
                Log.e("SyncDebug", "❌ Firestore: Gagal push stock movements: ${e.message}")
            }

            try {
                val response = api.syncStockMovements(payloads)
                if (response.isSuccessful) {
                    Log.d("SyncDebug", "✅ Laravel: Semua mutasi stok masuk ke MySQL!")
                    val writeDb = dbHelper.writableDatabase
                    writeDb.beginTransaction()
                    try {
                        movementsList.forEach { sm ->
                            writeDb.execSQL("UPDATE stock_movements SET isSynced = 1 WHERE id = ?", arrayOf(sm.id))
                        }
                        writeDb.setTransactionSuccessful()
                    } finally {
                        writeDb.endTransaction()
                    }
                }
            } catch (e: Exception) { Log.e("SyncDebug", "❌ Laravel: Gagal push stock movements: ${e.message}") }
        }
    }

    private suspend fun pushSales(firestore: FirestoreManager) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM sales WHERE isSynced = 0", null)
        val payloads = mutableListOf<SaleSyncPayload>()
        val salesList = mutableListOf<Pair<SaleEntity, List<SaleItemEntity>>>()
        while (cursor.moveToNext()) {
            val sale = cursor.toSale()
            val items = mutableListOf<SaleItemEntity>()
            val itemCursor = db.rawQuery("SELECT * FROM sale_items WHERE saleId = ?", arrayOf(sale.id.toString()))
            while (itemCursor.moveToNext()) { items.add(itemCursor.toSaleItem()) }
            itemCursor.close()

            salesList.add(Pair(sale, items))
            payloads.add(SaleSyncPayload(sale.id, sale.transactionId, sale.cashierId, sale.subtotal, sale.discount, sale.tax, sale.total, sale.paymentMethod, sale.status, sale.createdAtEpochMs, items.map { SaleItemSyncPayload(it.id, it.productId, it.productName, it.unitPrice, it.quantity, it.lineTotal) }))
        }
        cursor.close()

        if (payloads.isNotEmpty()) {
            try {
                salesList.forEach { (sale, items) -> firestore.syncSale(sale, items) }
                Log.d("SyncDebug", "✅ Firestore: Pushed ${salesList.size} sales!")
            } catch (e: Exception) {
                Log.e("SyncDebug", "❌ Firestore: Gagal push sales: ${e.message}")
            }

            try {
                val response = api.syncSales(payloads)
                if (response.isSuccessful) {
                    Log.d("SyncDebug", "✅ Laravel: Semua transaksi masuk ke MySQL!")
                    val writeDb = dbHelper.writableDatabase
                    writeDb.beginTransaction()
                    try {
                        salesList.forEach { (sale, items) ->
                            writeDb.execSQL("UPDATE sales SET isSynced = 1 WHERE id = ?", arrayOf(sale.id))
                        }
                        writeDb.setTransactionSuccessful()
                    } finally {
                        writeDb.endTransaction()
                    }
                }
            } catch (e: Exception) { Log.e("SyncDebug", "❌ Laravel: Gagal push sales: ${e.message}") }
        }
    }

    private suspend fun pushAuditLogs(firestore: FirestoreManager) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM audit_logs WHERE isSynced = 0", null)
        val payloads = mutableListOf<AuditLogSyncPayload>()
        val logsList = mutableListOf<AuditLogEntity>()
        while (cursor.moveToNext()) {
            val log = cursor.toAudit()
            logsList.add(log)
            payloads.add(AuditLogSyncPayload(log.id, log.userId, log.action, log.entity, log.entityId, log.detail, log.createdAtEpochMs))
        }
        cursor.close()

        if (payloads.isNotEmpty()) {
            try {
                logsList.forEach { log -> firestore.syncAuditLog(log) }
                Log.d("SyncDebug", "✅ Firestore: Pushed ${logsList.size} audit logs!")
            } catch (e: Exception) {
                Log.e("SyncDebug", "❌ Firestore: Gagal push audit logs: ${e.message}")
            }

            try {
                val response = api.syncAuditLogs(payloads)
                if (response.isSuccessful) {
                    Log.d("SyncDebug", "✅ Laravel: Semua audit logs masuk ke MySQL!")
                    val writeDb = dbHelper.writableDatabase
                    writeDb.beginTransaction()
                    try {
                        logsList.forEach { log ->
                            writeDb.execSQL("UPDATE audit_logs SET isSynced = 1 WHERE id = ?", arrayOf(log.id))
                        }
                        writeDb.setTransactionSuccessful()
                    } finally {
                        writeDb.endTransaction()
                    }
                }
            } catch (e: Exception) { Log.e("SyncDebug", "❌ Laravel: Gagal push audit logs: ${e.message}") }
        }
    }

    private suspend fun pushPromos(firestore: FirestoreManager) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM promos WHERE isSynced = 0", null)
        val payloads = mutableListOf<PromoSyncPayload>()
        val promosList = mutableListOf<PromoEntity>()
        while (cursor.moveToNext()) {
            val p = cursor.toPromo()
            promosList.add(p)
            payloads.add(PromoSyncPayload(p.id, p.code, p.name, p.description, p.discountPercent, p.validUntilEpochMs, if (p.isActive) 1 else 0))
        }
        cursor.close()

        if (payloads.isNotEmpty()) {
            try {
                promosList.forEach { p -> firestore.syncPromo(p) }
                Log.d("SyncDebug", "✅ Firestore: Pushed ${promosList.size} promos!")
            } catch (e: Exception) {
                Log.e("SyncDebug", "❌ Firestore: Gagal push promos: ${e.message}")
            }

            try {
                val response = api.syncPromos(payloads)
                if (response.isSuccessful) {
                    Log.d("SyncDebug", "✅ Laravel: Semua promo masuk ke MySQL!")
                    val writeDb = dbHelper.writableDatabase
                    writeDb.beginTransaction()
                    try {
                        promosList.forEach { p ->
                            writeDb.execSQL("UPDATE promos SET isSynced = 1 WHERE id = ?", arrayOf(p.id))
                        }
                        writeDb.setTransactionSuccessful()
                    } finally {
                        writeDb.endTransaction()
                    }
                }
            } catch (e: Exception) { Log.e("SyncDebug", "❌ Laravel: Gagal push promos: ${e.message}") }
        }
    }

    private suspend fun pushSettings(firestore: FirestoreManager) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM settings LIMIT 1", null)
        if (cursor.moveToFirst()) {
            val s = cursor.toSettings()
            if (cursor.getInt(cursor.getColumnIndexOrThrow("isSynced")) == 0) {
                try {
                    firestore.syncSettings(s)
                    Log.d("SyncDebug", "✅ Firestore: Settings pushed!")
                } catch (e: Exception) {
                    Log.e("SyncDebug", "❌ Firestore: Gagal push settings: ${e.message}")
                }

                try {
                    val resp = api.syncSettings(SettingsSyncPayload(s.koperasiName, s.koperasiAddress, s.koperasiPhone, s.taxPercent, s.discountPercent, s.shuParameter, s.latitude, s.longitude, s.updatedAtEpochMs))
                    if (resp.isSuccessful) {
                        dbHelper.writableDatabase.execSQL("UPDATE settings SET isSynced = 1 WHERE id = 1")
                        Log.d("SyncDebug", "✅ Laravel: Settings synced!")
                    }
                } catch (e: Exception) {
                    Log.e("SyncDebug", "❌ Laravel: Gagal push settings: ${e.message}")
                }
            }
        }
        cursor.close()
    }

    // --- CURSOR HELPERS ---
    private fun android.database.Cursor.toUser() = UserEntity(getLong(getColumnIndexOrThrow("id")), getString(getColumnIndexOrThrow("name")), getString(getColumnIndexOrThrow("username")), getString(getColumnIndexOrThrow("passwordHash")), getString(getColumnIndexOrThrow("salt")), Role.valueOf(getString(getColumnIndexOrThrow("role"))), getInt(getColumnIndexOrThrow("isActive")) == 1, getInt(getColumnIndexOrThrow("needsPasswordReset")) == 1, getInt(getColumnIndexOrThrow("isSynced")) == 1, getLong(getColumnIndexOrThrow("createdAtEpochMs")))
    private fun android.database.Cursor.toMember() = MemberEntity(getLong(getColumnIndexOrThrow("id")), getString(getColumnIndexOrThrow("memberNo")), getString(getColumnIndexOrThrow("name")), getString(getColumnIndexOrThrow("phone")), getString(getColumnIndexOrThrow("address")), getInt(getColumnIndexOrThrow("isActive")) == 1, getInt(getColumnIndexOrThrow("isSynced")) == 1, getLong(getColumnIndexOrThrow("createdAtEpochMs")))
    private fun android.database.Cursor.toProduct() = ProductEntity(getLong(getColumnIndexOrThrow("id")), getString(getColumnIndexOrThrow("barcode")), getString(getColumnIndexOrThrow("name")), getString(getColumnIndexOrThrow("category")), getLong(getColumnIndexOrThrow("price")), getLong(getColumnIndexOrThrow("purchasePrice")), getLong(getColumnIndexOrThrow("stock")), getLong(getColumnIndexOrThrow("minimumStock")), if (isNull(getColumnIndexOrThrow("expiredDateEpochMs"))) null else getLong(getColumnIndexOrThrow("expiredDateEpochMs")), getString(getColumnIndexOrThrow("imagePath")), getInt(getColumnIndexOrThrow("isSynced")) == 1, getLong(getColumnIndexOrThrow("createdAtEpochMs")))
    private fun android.database.Cursor.toStockMovement() = StockMovementEntity(getLong(getColumnIndexOrThrow("id")), getLong(getColumnIndexOrThrow("productId")), if (isNull(getColumnIndexOrThrow("userId"))) null else getLong(getColumnIndexOrThrow("userId")), getString(getColumnIndexOrThrow("type")), getLong(getColumnIndexOrThrow("quantityDelta")), getString(getColumnIndexOrThrow("note")), getInt(getColumnIndexOrThrow("isSynced")) == 1, getLong(getColumnIndexOrThrow("createdAtEpochMs")))
    private fun android.database.Cursor.toSale() = SaleEntity(getLong(getColumnIndexOrThrow("id")), getString(getColumnIndexOrThrow("transactionId")), if (isNull(getColumnIndexOrThrow("cashierId"))) null else getLong(getColumnIndexOrThrow("cashierId")), getLong(getColumnIndexOrThrow("subtotal")), getLong(getColumnIndexOrThrow("discount")), getLong(getColumnIndexOrThrow("tax")), getLong(getColumnIndexOrThrow("total")), getString(getColumnIndexOrThrow("paymentMethod")), getString(getColumnIndexOrThrow("status")), getInt(getColumnIndexOrThrow("isSynced")) == 1, getLong(getColumnIndexOrThrow("createdAtEpochMs")))
    private fun android.database.Cursor.toSaleItem() = SaleItemEntity(getLong(getColumnIndexOrThrow("id")), getLong(getColumnIndexOrThrow("saleId")), if (isNull(getColumnIndexOrThrow("productId"))) null else getLong(getColumnIndexOrThrow("productId")), getString(getColumnIndexOrThrow("productName")), getLong(getColumnIndexOrThrow("unitPrice")), getLong(getColumnIndexOrThrow("quantity")), getLong(getColumnIndexOrThrow("lineTotal")))
    private fun android.database.Cursor.toAudit() = AuditLogEntity(getLong(getColumnIndexOrThrow("id")), if (isNull(getColumnIndexOrThrow("userId"))) null else getLong(getColumnIndexOrThrow("userId")), getString(getColumnIndexOrThrow("action")), getString(getColumnIndexOrThrow("entity")), if (isNull(getColumnIndexOrThrow("entityId"))) null else getLong(getColumnIndexOrThrow("entityId")), getString(getColumnIndexOrThrow("detail")), getInt(getColumnIndexOrThrow("isSynced")) == 1, getLong(getColumnIndexOrThrow("createdAtEpochMs")))
    private fun android.database.Cursor.toPromo() = PromoEntity(getLong(getColumnIndexOrThrow("id")), getString(getColumnIndexOrThrow("code")), getString(getColumnIndexOrThrow("name")), getString(getColumnIndexOrThrow("description")), getDouble(getColumnIndexOrThrow("discountPercent")), getLong(getColumnIndexOrThrow("validUntilEpochMs")), getInt(getColumnIndexOrThrow("isSynced")) == 1, getInt(getColumnIndexOrThrow("isActive")) == 1)
    private fun android.database.Cursor.toSettings() = SettingsEntity(1, getString(getColumnIndexOrThrow("koperasiName")), getString(getColumnIndexOrThrow("koperasiAddress")), getString(getColumnIndexOrThrow("koperasiPhone")), null, getDouble(getColumnIndexOrThrow("taxPercent")), getDouble(getColumnIndexOrThrow("discountPercent")), getDouble(getColumnIndexOrThrow("shuParameter")), if (isNull(getColumnIndexOrThrow("latitude"))) null else getDouble(getColumnIndexOrThrow("latitude")), if (isNull(getColumnIndexOrThrow("longitude"))) null else getDouble(getColumnIndexOrThrow("longitude")), getInt(getColumnIndexOrThrow("isSynced")) == 1, getLong(getColumnIndexOrThrow("updatedAtEpochMs")))

    private fun UserEntity.toSyncPayload() = UserSyncPayload(id, name, username, passwordHash, salt, role.name, if (isActive) 1 else 0, if (needsPasswordReset) 1 else 0, createdAtEpochMs)
}
