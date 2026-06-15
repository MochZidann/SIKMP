package com.kopdes.kopdesjajar.data.db

import com.kopdes.kopdesjajar.data.model.Role

data class UserEntity(
    val id: Long = 0,
    val name: String,
    val username: String,
    val passwordHash: String,
    val salt: String,
    val role: Role,
    val isActive: Boolean = true,
    val needsPasswordReset: Boolean = false,
    val isSynced: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class MemberEntity(
    val id: Long = 0,
    val memberNo: String,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val isActive: Boolean = true,
    val isSynced: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class ProductEntity(
    val id: Long = 0,
    val barcode: String? = null,
    val name: String,
    val category: String,
    val price: Long,
    val purchasePrice: Long = 0,
    val stock: Long,
    val minimumStock: Long = 0,
    val expiredDateEpochMs: Long? = null,
    val imagePath: String? = null,
    val isSynced: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class CategoryEntity(
    val id: Long = 0,
    val name: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

data class StockMovementEntity(
    val id: Long = 0,
    val productId: Long,
    val userId: Long?,
    val type: String, // "IN", "OUT", "ADJUST", "SALE"
    val quantityDelta: Long,
    val note: String? = null,
    val isSynced: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class SaleEntity(
    val id: Long = 0,
    val transactionId: String, // Structured ID: TRX-yyyyMMdd-XXXX
    val cashierId: Long?,
    val subtotal: Long,
    val discount: Long,
    val tax: Long,
    val total: Long,
    val paymentMethod: String = "TUNAI", // "TUNAI", "QRIS", "TRANSFER"
    val status: String = "SUCCESS", // "SUCCESS", "CANCELLED"
    val isSynced: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class SaleItemEntity(
    val id: Long = 0,
    val saleId: Long,
    val productId: Long?,
    val productName: String,
    val unitPrice: Long,
    val quantity: Long,
    val lineTotal: Long
)

data class SettingsEntity(
    val id: Long = 1,
    val koperasiName: String = "",
    val koperasiAddress: String = "",
    val koperasiPhone: String = "",
    val qrisImagePath: String? = null, // Path to QRIS Image
    val taxPercent: Double = 0.0,
    val discountPercent: Double = 0.0,
    val shuParameter: Double = 0.0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isSynced: Boolean = false,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

data class AuditLogEntity(
    val id: Long = 0,
    val userId: Long?,
    val action: String,
    val entity: String,
    val entityId: Long?,
    val detail: String? = null,
    val isSynced: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class PromoEntity(
    val id: Long = 0,
    val code: String = "",
    val name: String,
    val description: String? = null,
    val discountPercent: Double,
    val validUntilEpochMs: Long,
    val promoType: String = "TRANSACTION", // "TRANSACTION", "PRODUCT"
    val minimumPurchase: Long = 0,
    val productId: Long? = null,
    val isSynced: Boolean = false,
    val isActive: Boolean = true
)
