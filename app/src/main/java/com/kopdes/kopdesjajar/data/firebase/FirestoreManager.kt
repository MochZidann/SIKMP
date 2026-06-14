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

    suspend fun getAllProducts(): List<ProductEntity> = emptyList()
    suspend fun getAllCategories(): List<CategoryEntity> = emptyList()
    suspend fun getAllMembers(): List<MemberEntity> = emptyList()
    suspend fun getAllPromos(): List<PromoEntity> = emptyList()
    suspend fun getRemoteSettings(): SettingsEntity? = null
    suspend fun getAllSales(): List<Pair<SaleEntity, List<SaleItemEntity>>> = emptyList()
    suspend fun getAllStockMovements(): List<StockMovementEntity> = emptyList()
    suspend fun getAllAuditLogs(): List<AuditLogEntity> = emptyList()

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

    suspend fun syncProduct(product: ProductEntity) {}
    suspend fun syncSale(sale: SaleEntity, items: List<SaleItemEntity>) {}
    suspend fun syncCategory(c: CategoryEntity) {}
    suspend fun syncMember(m: MemberEntity) {}
    suspend fun syncPromo(p: PromoEntity) {}
    suspend fun syncSettings(s: SettingsEntity) {}
    suspend fun syncAuditLog(l: AuditLogEntity) {}
    suspend fun syncStockMovement(sm: StockMovementEntity) {}

    suspend fun deleteUser(username: String) {
        try { usersCol.document(username).delete().await() }
        catch (e: Exception) { Log.e("FirebaseDebug", "❌ Gagal delete user: ${e.message}") }
    }

    suspend fun deleteProduct(barcodeOrName: String) {}
    suspend fun deleteMember(memberNo: String) {}
    suspend fun deletePromo(code: String) {}
    suspend fun deleteCategory(name: String) {}
}
