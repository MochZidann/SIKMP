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

    suspend fun getUser(username: String): UserEntity? = try {
        val doc = usersCol.document(username).get().await()
        if (doc.exists()) {
            val d = doc.data!!
            UserEntity(
                name = d["name"] as? String ?: "",
                username = doc.id,
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
        val snapshot = usersCol.get().await()
        snapshot.documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            try {
                UserEntity(
                    id = d["id"]?.toLongSafe() ?: 0L,
                    name = d["name"] as? String ?: "Unknown",
                    username = doc.id,
                    passwordHash = d["passwordHash"] as? String ?: "",
                    salt = d["salt"] as? String ?: "",
                    role = Role.valueOf(d["role"] as? String ?: "KASIR"),
                    isActive = d["isActive"] as? Boolean ?: true,
                    needsPasswordReset = d["needsPasswordReset"] as? Boolean ?: false,
                    createdAtEpochMs = d["createdAtEpochMs"].toLongSafe()
                )
            } catch (e: Exception) {
                Log.e("FirebaseDebug", "Error parsing user doc ${doc.id}: ${e.message}")
                null
            }
        }
    } catch (e: Exception) { 
        Log.e("FirebaseDebug", "❌ Gagal pull users: ${e.message}")
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
            Log.d("FirebaseDebug", "✅ User ${user.username} synced to Firestore")
        } catch (e: Exception) { Log.e("FirebaseDebug", "❌ Gagal push user: ${e.message}") }
    }

    suspend fun syncProduct(p: ProductEntity) {
        try {
            productsCol.document(p.id.toString()).set(mapOf(
                "id" to p.id, "barcode" to p.barcode, "name" to p.name, "category" to p.category,
                "price" to p.price, "stock" to p.stock, "purchasePrice" to p.purchasePrice,
                "minimumStock" to p.minimumStock, "expiredDate" to p.expiredDateEpochMs,
                "imagePath" to p.imagePath, "updatedAt" to System.currentTimeMillis()
            )).await()
        } catch (e: Exception) { Log.e("FirebaseDebug", "❌ Gagal push product: ${e.message}") }
    }

    suspend fun syncSale(sale: SaleEntity, items: List<SaleItemEntity>) {
        try {
            salesCol.document(sale.transactionId).set(mapOf(
                "id" to sale.id, "transactionId" to sale.transactionId, "total" to sale.total,
                "items" to items.map { mapOf("name" to it.productName, "qty" to it.quantity, "price" to it.unitPrice) },
                "createdAt" to sale.createdAtEpochMs
            )).await()
        } catch (e: Exception) { Log.e("FirebaseDebug", "❌ Gagal push sale: ${e.message}") }
    }

    suspend fun deleteUser(username: String): Boolean {
        return try {
            usersCol.document(username).delete().await()
            Log.d("FirebaseDebug", "✅ User $username deleted from Firestore")
            true
        } catch (e: Exception) {
            Log.e("FirebaseDebug", "❌ Gagal delete user $username: ${e.message}")
            false
        }
    }

    suspend fun deleteMember(memberNo: String): Boolean {
        return try {
            membersCol.document(memberNo).delete().await()
            Log.d("FirebaseDebug", "✅ Member $memberNo deleted from Firestore")
            true
        } catch (e: Exception) {
            Log.e("FirebaseDebug", "❌ Gagal delete member $memberNo: ${e.message}")
            false
        }
    }

    suspend fun deletePromo(promoCode: String): Boolean {
        return try {
            promosCol.document(promoCode).delete().await()
            Log.d("FirebaseDebug", "✅ Promo $promoCode deleted from Firestore")
            true
        } catch (e: Exception) {
            Log.e("FirebaseDebug", "❌ Gagal delete promo $promoCode: ${e.message}")
            false
        }
    }

    suspend fun syncCategory(c: CategoryEntity) {
        try { categoriesCol.document(c.name).set(c).await() } catch (e: Exception) {}
    }

    suspend fun syncMember(m: MemberEntity) {
        try { membersCol.document(m.memberNo).set(m).await() } catch (e: Exception) {}
    }

    suspend fun syncPromo(p: PromoEntity) {
        try { promosCol.document(p.code).set(p).await() } catch (e: Exception) {}
    }

    suspend fun syncSettings(s: SettingsEntity) {
        try { settingsDoc.set(s).await() } catch (e: Exception) {}
    }

    suspend fun syncAuditLog(l: AuditLogEntity) {
        try { auditCol.document(l.id.toString()).set(l).await() } catch (e: Exception) {}
    }

    suspend fun syncStockMovement(sm: StockMovementEntity) {
        try { movementsCol.document(sm.id.toString()).set(sm).await() } catch (e: Exception) {}
    }
}
