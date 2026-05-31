package com.kopdes.kopdesjajar.data.firebase

import android.util.Log
import com.kopdes.kopdesjajar.data.db.*
import com.kopdes.kopdesjajar.data.model.Role
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.tasks.await

class FirestoreManager {
    private val db = FirebaseFirestore.getInstance().apply {
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
        firestoreSettings = settings
    }
    
    private val usersCol = db.collection("users")
    private val productsCol = db.collection("products")
    private val categoriesCol = db.collection("categories")
    private val salesCol = db.collection("sales")
    private val membersCol = db.collection("members")
    private val promosCol = db.collection("promos")
    private val movementsCol = db.collection("movements")
    private val auditCol = db.collection("audit_logs")
    private val settingsDoc = db.collection("config").document("koperasi_profile")

    private fun Any?.toLongSafe(): Long = (this as? Number)?.toLong() ?: 0L
    private fun Any?.toDoubleSafe(): Double = (this as? Number)?.toDouble() ?: 0.0

    // --- PULL DATA (Untuk Restore HP Baru / Login) ---

    suspend fun getUser(username: String): UserEntity? = try {
        val doc = usersCol.document(username).get().await()
        if (doc.exists()) {
            val d = doc.data!!
            UserEntity(
                name = d["name"] as? String ?: "",
                username = d["username"] as? String ?: "",
                passwordHash = d["passwordHash"] as? String ?: "",
                salt = d["salt"] as? String ?: "",
                role = Role.valueOf(d["role"] as? String ?: "KASIR"),
                isActive = d["isActive"] as? Boolean ?: true,
                needsPasswordReset = d["needsPasswordReset"] as? Boolean ?: false,
                createdAtEpochMs = d["createdAtEpochMs"].toLongSafe()
            )
        } else null
    } catch (e: Exception) {
        Log.e("FirebaseDebug", "❌ Gagal getUser $username: ${e.message}")
        null
    }

    suspend fun getAllUsers(): List<UserEntity> = try {
        usersCol.get().await().documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            UserEntity(
                id = d["id"]?.toLongSafe() ?: 0L,
                name = d["name"] as String,
                username = d["username"] as String,
                passwordHash = d["passwordHash"] as? String ?: "",
                salt = d["salt"] as? String ?: "",
                role = Role.valueOf(d["role"] as? String ?: "KASIR"),
                isActive = d["isActive"] as? Boolean ?: true,
                needsPasswordReset = d["needsPasswordReset"] as? Boolean ?: false,
                createdAtEpochMs = d["createdAtEpochMs"].toLongSafe()
            )
        }
    } catch (e: Exception) { 
        Log.e("FirebaseDebug", "❌ Gagal pull users: ${e.message}")
        emptyList() 
    }

    suspend fun getAllProducts(): List<ProductEntity> = try {
        productsCol.get().await().documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            ProductEntity(
                id = d["id"]?.toLongSafe() ?: 0L,
                barcode = d["barcode"] as? String,
                name = d["name"] as? String ?: "",
                category = d["category"] as? String ?: "",
                price = d["price"].toLongSafe(),
                stock = d["stock"].toLongSafe(),
                purchasePrice = d["purchasePrice"].toLongSafe(),
                minimumStock = d["minimumStock"].toLongSafe(),
                expiredDateEpochMs = if (d["expiredDateEpochMs"] == null) null else d["expiredDateEpochMs"].toLongSafe(),
                imagePath = d["imagePath"] as? String,
                createdAtEpochMs = d["createdAtEpochMs"].toLongSafe(),
                isSynced = true
            )
        }
    } catch (e: Exception) { 
        Log.e("FirebaseDebug", "❌ Gagal pull products: ${e.message}")
        emptyList() 
    }

    suspend fun getAllCategories(): List<CategoryEntity> = try {
        categoriesCol.get().await().documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            CategoryEntity(
                id = d["id"]?.toLongSafe() ?: 0L,
                name = d["name"] as String, 
                createdAtEpochMs = d["createdAtEpochMs"].toLongSafe()
            )
        }
    } catch (e: Exception) { emptyList() }

    suspend fun getAllMembers(): List<MemberEntity> = try {
        membersCol.get().await().documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            MemberEntity(
                id = d["id"]?.toLongSafe() ?: 0L,
                memberNo = d["memberNo"] as String,
                name = d["name"] as String,
                phone = d["phone"] as? String,
                address = d["address"] as? String,
                isActive = d["isActive"] as? Boolean ?: true,
                createdAtEpochMs = d["createdAtEpochMs"].toLongSafe(),
                isSynced = true
            )
        }
    } catch (e: Exception) { emptyList() }

    suspend fun getAllPromos(): List<PromoEntity> = try {
        promosCol.get().await().documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            PromoEntity(
                id = d["id"]?.toLongSafe() ?: 0L,
                code = d["code"] as String,
                name = d["name"] as String,
                description = d["description"] as? String,
                discountPercent = d["discountPercent"].toDoubleSafe(),
                validUntilEpochMs = d["validUntilEpochMs"].toLongSafe(),
                isActive = d["isActive"] as? Boolean ?: true,
                isSynced = true
            )
        }
    } catch (e: Exception) { emptyList() }

    suspend fun getRemoteSettings(): SettingsEntity? = try {
        val doc = settingsDoc.get().await()
        if (doc.exists()) {
            val d = doc.data ?: return null
            SettingsEntity(
                koperasiName = d["koperasiName"] as? String ?: "",
                koperasiAddress = d["koperasiAddress"] as? String ?: "",
                koperasiPhone = d["koperasiPhone"] as? String ?: "",
                taxPercent = d["taxPercent"].toDoubleSafe(),
                discountPercent = d["discountPercent"].toDoubleSafe(),
                shuParameter = d["shuParameter"].toDoubleSafe(),
                latitude = d["latitude"]?.toDoubleSafe(),
                longitude = d["longitude"]?.toDoubleSafe(),
                updatedAtEpochMs = d["updatedAtEpochMs"].toLongSafe(),
                isSynced = true
            )
        } else null
    } catch (e: Exception) { null }

    suspend fun getAllSales(): List<Pair<SaleEntity, List<SaleItemEntity>>> = try {
        salesCol.get().await().documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            val sale = SaleEntity(
                id = d["id"]?.toLongSafe() ?: 0L,
                transactionId = d["transactionId"] as? String ?: "",
                cashierId = if (d["cashierId"] == null) null else d["cashierId"].toLongSafe(),
                subtotal = d["subtotal"].toLongSafe(),
                discount = d["discount"].toLongSafe(),
                tax = d["tax"].toLongSafe(),
                total = d["total"].toLongSafe(),
                paymentMethod = d["paymentMethod"] as? String ?: "TUNAI",
                status = d["status"] as? String ?: "SUCCESS",
                createdAtEpochMs = d["createdAtEpochMs"].toLongSafe(),
                isSynced = true
            )
            val itemsList = d["items"] as? List<Map<String, Any>> ?: emptyList()
            val items = itemsList.map { item ->
                SaleItemEntity(
                    id = item["id"]?.toLongSafe() ?: 0L,
                    productId = if (item["productId"] == null) null else item["productId"].toLongSafe(),
                    productName = item["productName"] as? String ?: "",
                    unitPrice = item["unitPrice"].toLongSafe(),
                    quantity = item["quantity"].toLongSafe(),
                    lineTotal = item["lineTotal"].toLongSafe(),
                    saleId = 0
                )
            }
            Pair(sale, items)
        }
    } catch (e: Exception) {
        Log.e("FirebaseDebug", "❌ Gagal pull sales: ${e.message}")
        emptyList()
    }

    suspend fun getAllStockMovements(): List<StockMovementEntity> = try {
        movementsCol.get().await().documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            StockMovementEntity(
                id = d["id"]?.toLongSafe() ?: doc.id.toLongSafe(),
                productId = d["productId"].toLongSafe(),
                userId = if (d["userId"] == null) null else d["userId"].toLongSafe(),
                type = d["type"] as? String ?: "IN",
                quantityDelta = d["quantityDelta"].toLongSafe(),
                note = d["note"] as? String,
                createdAtEpochMs = d["createdAtEpochMs"].toLongSafe(),
                isSynced = true
            )
        }
    } catch (e: Exception) {
        Log.e("FirebaseDebug", "❌ Gagal pull movements: ${e.message}")
        emptyList()
    }

    suspend fun getAllAuditLogs(): List<AuditLogEntity> = try {
        auditCol.get().await().documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            AuditLogEntity(
                id = d["id"]?.toLongSafe() ?: doc.id.toLongSafe(),
                userId = if (d["userId"] == null) null else d["userId"].toLongSafe(),
                action = d["action"] as? String ?: "",
                entity = d["entity"] as? String ?: "",
                entityId = if (d["entityId"] == null) null else d["entityId"].toLongSafe(),
                detail = d["detail"] as? String,
                createdAtEpochMs = d["createdAtEpochMs"].toLongSafe(),
                isSynced = true
            )
        }
    } catch (e: Exception) {
        Log.e("FirebaseDebug", "❌ Gagal pull audit logs: ${e.message}")
        emptyList()
    }

    // --- SYNC FUNCTIONS (PUSH) ---


    suspend fun syncUser(user: UserEntity) {
        try {
            usersCol.document(user.username).set(mapOf(
                "id" to user.id, "name" to user.name, "username" to user.username, "passwordHash" to user.passwordHash,
                "salt" to user.salt, "role" to user.role.name, "isActive" to user.isActive,
                "needsPasswordReset" to user.needsPasswordReset, "createdAtEpochMs" to user.createdAtEpochMs,
                "updatedAt" to System.currentTimeMillis()
            )).await()
            Log.d("FirebaseDebug", "✅ User ${user.username} pushed")
        } catch (e: Exception) { Log.e("FirebaseDebug", "❌ Gagal push user: ${e.message}") }
    }

    suspend fun syncProduct(product: ProductEntity) {
        try {
            val docId = product.barcode?.takeIf { it.isNotBlank() } ?: product.name.replace("/", "-")
            productsCol.document(docId).set(mapOf(
                "id" to product.id, "barcode" to product.barcode, "name" to product.name, "category" to product.category,
                "price" to product.price, "purchasePrice" to product.purchasePrice, "stock" to product.stock,
                "minimumStock" to product.minimumStock, "expiredDateEpochMs" to product.expiredDateEpochMs,
                "imagePath" to product.imagePath, "createdAtEpochMs" to product.createdAtEpochMs,
                "updatedAt" to System.currentTimeMillis()
            )).await()
            Log.d("FirebaseDebug", "✅ Produk ${product.name} pushed")
        } catch (e: Exception) { Log.e("FirebaseDebug", "❌ Gagal sync produk: ${e.message}") }
    }

    suspend fun syncSale(sale: SaleEntity, items: List<SaleItemEntity>) {
        try {
            val data = mapOf(
                "id" to sale.id, "transactionId" to sale.transactionId, "total" to sale.total, "subtotal" to sale.subtotal,
                "tax" to sale.tax, "discount" to sale.discount, "paymentMethod" to sale.paymentMethod,
                "cashierId" to sale.cashierId, "createdAtEpochMs" to sale.createdAtEpochMs,
                "items" to items.map { mapOf(
                    "id" to it.id, "productId" to it.productId, "productName" to it.productName,
                    "quantity" to it.quantity, "unitPrice" to it.unitPrice, "lineTotal" to it.lineTotal
                ) }
            )
            salesCol.document(sale.transactionId).set(data).await()
            Log.d("FirebaseDebug", "✅ Sale ${sale.transactionId} pushed")
        } catch (e: Exception) { Log.e("FirebaseDebug", "❌ Gagal sync sale: ${e.message}") }
    }

    suspend fun syncCategory(c: CategoryEntity) {
        try { categoriesCol.document(c.name).set(mapOf("id" to c.id, "name" to c.name, "createdAtEpochMs" to c.createdAtEpochMs)).await() }
        catch (e: Exception) { Log.e("FirebaseDebug", "❌ Gagal sync category: ${e.message}") }
    }

    suspend fun syncMember(m: MemberEntity) {
        try { membersCol.document(m.memberNo).set(mapOf(
            "id" to m.id, "memberNo" to m.memberNo, "name" to m.name, "phone" to m.phone, "address" to m.address,
            "isActive" to m.isActive, "createdAtEpochMs" to m.createdAtEpochMs
        )).await() }
        catch (e: Exception) { Log.e("FirebaseDebug", "❌ Gagal sync member: ${e.message}") }
    }

    suspend fun syncPromo(p: PromoEntity) {
        try { promosCol.document(p.code).set(mapOf(
            "id" to p.id, "code" to p.code, "name" to p.name, "description" to p.description,
            "discountPercent" to p.discountPercent, "validUntilEpochMs" to p.validUntilEpochMs, "isActive" to p.isActive
        )).await() }
        catch (e: Exception) { Log.e("FirebaseDebug", "❌ Gagal sync promo: ${e.message}") }
    }

    suspend fun syncSettings(s: SettingsEntity) {
        try { settingsDoc.set(mapOf(
            "id" to s.id, "koperasiName" to s.koperasiName, "koperasiAddress" to s.koperasiAddress, "koperasiPhone" to s.koperasiPhone,
            "taxPercent" to s.taxPercent, "discountPercent" to s.discountPercent, "shuParameter" to s.shuParameter,
            "latitude" to s.latitude, "longitude" to s.longitude, "updatedAtEpochMs" to s.updatedAtEpochMs
        )).await() }
        catch (e: Exception) { Log.e("FirebaseDebug", "❌ Gagal sync settings: ${e.message}") }
    }

    suspend fun syncAuditLog(l: AuditLogEntity) {
        try { auditCol.document(l.id.toString()).set(mapOf(
            "id" to l.id, "userId" to l.userId, "action" to l.action, "entity" to l.entity,
            "entityId" to l.entityId, "detail" to l.detail, "createdAtEpochMs" to l.createdAtEpochMs
        )).await() }
        catch (e: Exception) { Log.e("FirebaseDebug", "❌ Gagal sync audit: ${e.message}") }
    }

    suspend fun syncStockMovement(sm: StockMovementEntity) {
        try { movementsCol.document(sm.id.toString()).set(mapOf(
            "id" to sm.id, "productId" to sm.productId, "userId" to sm.userId, "type" to sm.type,
            "quantityDelta" to sm.quantityDelta, "note" to sm.note, "createdAtEpochMs" to sm.createdAtEpochMs
        )).await() }
        catch (e: Exception) { Log.e("FirebaseDebug", "❌ Gagal sync movement: ${e.message}") }
    }

    suspend fun deleteUser(username: String) {
        try { usersCol.document(username).delete().await() }
        catch (e: Exception) { Log.e("FirebaseDebug", "❌ Gagal delete user: ${e.message}") }
    }

    suspend fun deleteProduct(barcodeOrName: String) {
        try { productsCol.document(barcodeOrName).delete().await() }
        catch (e: Exception) { Log.e("FirebaseDebug", "❌ Gagal delete product: ${e.message}") }
    }

    suspend fun deleteMember(memberNo: String) {
        try { membersCol.document(memberNo).delete().await() }
        catch (e: Exception) { Log.e("FirebaseDebug", "❌ Gagal delete member: ${e.message}") }
    }

    suspend fun deletePromo(code: String) {
        try { promosCol.document(code).delete().await() }
        catch (e: Exception) { Log.e("FirebaseDebug", "❌ Gagal delete promo: ${e.message}") }
    }

    suspend fun deleteCategory(name: String) {
        try { categoriesCol.document(name).delete().await() }
        catch (e: Exception) { Log.e("FirebaseDebug", "❌ Gagal delete category: ${e.message}") }
    }
}
