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

// Wajib pakai @SerializedName agar format JSON sesuai dengan yang diminta Laravel API
data class ProductSyncPayload(
    @SerializedName("id") val id: Long,
    @SerializedName("barcode") val barcode: String?,
    @SerializedName("name") val name: String,
    @SerializedName("category") val category: String,
    @SerializedName("price") val price: Long,
    @SerializedName("stock") val stock: Long,
    @SerializedName("minimum_stock") val minimumStock: Long,
    @SerializedName("expired_date_epoch_ms") val expiredDateEpochMs: Long?,
    @SerializedName("image_path") val imagePath: String?,
    @SerializedName("purchase_price") val purchasePrice: Long,
    @SerializedName("created_at_epoch_ms") val createdAtEpochMs: Long,
    @SerializedName("imageBase64") val imageData: String? = null
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