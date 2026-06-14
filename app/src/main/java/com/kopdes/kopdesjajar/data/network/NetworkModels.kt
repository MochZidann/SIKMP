package com.kopdes.kopdesjajar.data.network

import com.google.gson.annotations.SerializedName

data class SyncResponse(
    val status: String,
    val message: String?
)

data class UserSyncPayload(
    val id: Long,
    val name: String,
    val username: String,
    val passwordHash: String,
    val salt: String,
    val role: String,
    val isActive: Int,
    val needsPasswordReset: Int,
    val createdAtEpochMs: Long
)

data class MemberSyncPayload(
    val id: Long,
    val memberNo: String,
    val name: String,
    val phone: String?,
    val address: String?,
    val isActive: Int,
    val createdAtEpochMs: Long
)

data class ProductSyncPayload(
    val id: Long,
    val barcode: String?,
    val name: String,
    val category: String,
    val price: Long,
    val stock: Long,
    val minimumStock: Long,
    val expiredDateEpochMs: Long?,
    val imagePath: String?,
    val purchasePrice: Long,
    val createdAtEpochMs: Long,
    val imageBase64: String? = null
)

data class CategorySyncPayload(
    val name: String,
    val createdAtEpochMs: Long
)

data class SaleSyncPayload(
    val id: Long,
    val transactionId: String,
    val cashierId: Long?,
    val subtotal: Long,
    val discount: Long,
    val tax: Long,
    val total: Long,
    val paymentMethod: String,
    val status: String,
    val createdAtEpochMs: Long,
    val items: List<SaleItemSyncPayload>
)

data class SaleItemSyncPayload(
    val id: Long,
    val productId: Long?,
    val productName: String,
    val unitPrice: Long,
    val quantity: Long,
    val lineTotal: Long
)

data class StockMovementSyncPayload(
    val id: Long,
    val productId: Long,
    val userId: Long?,
    val type: String,
    val quantityDelta: Long,
    val note: String?,
    val createdAtEpochMs: Long
)

data class SettingsSyncPayload(
    val koperasiName: String,
    val koperasiAddress: String,
    val koperasiPhone: String,
    val taxPercent: Double,
    val discountPercent: Double,
    val shuParameter: Double,
    val latitude: Double?,
    val longitude: Double?,
    val updatedAtEpochMs: Long
)

data class AuditLogSyncPayload(
    val id: Long,
    val userId: Long?,
    val action: String,
    val entity: String,
    val entityId: Long?,
    val detail: String?,
    val createdAtEpochMs: Long
)

data class PromoSyncPayload(
    val id: Long,
    val code: String,
    val name: String,
    val description: String?,
    val discountPercent: Double,
    val validUntilEpochMs: Long,
    val isActive: Int
)
