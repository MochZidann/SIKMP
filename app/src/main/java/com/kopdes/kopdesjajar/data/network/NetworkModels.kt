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
    @SerializedName(value = "password_hash", alternate = ["passwordHash"]) val passwordHash: String,
    val salt: String,
    val role: String,
    @SerializedName(value = "is_active", alternate = ["isActive"]) val isActive: Int,
    @SerializedName(value = "needs_password_reset", alternate = ["needsPasswordReset"]) val needsPasswordReset: Int,
    @SerializedName(value = "created_at_epoch_ms", alternate = ["createdAtEpochMs"]) val createdAtEpochMs: Long
)

data class MemberSyncPayload(
    val id: Long,
    @SerializedName(value = "member_no", alternate = ["memberNo"]) val memberNo: String,
    val name: String,
    val phone: String?,
    val address: String?,
    @SerializedName(value = "is_active", alternate = ["isActive"]) val isActive: Int,
    @SerializedName(value = "created_at_epoch_ms", alternate = ["createdAtEpochMs"]) val createdAtEpochMs: Long
)

data class ProductSyncPayload(
    val id: Long,
    val barcode: String?,
    val name: String,
    val category: String,
    val price: Long,
    val stock: Long,
    @SerializedName(value = "minimum_stock", alternate = ["minimumStock"]) val minimumStock: Long,
    @SerializedName(value = "expired_date_epoch_ms", alternate = ["expiredDateEpochMs"]) val expiredDateEpochMs: Long?,
    @SerializedName(value = "image_path", alternate = ["imagePath"]) val imagePath: String?,
    @SerializedName(value = "purchase_price", alternate = ["purchasePrice"]) val purchasePrice: Long,
    @SerializedName(value = "created_at_epoch_ms", alternate = ["createdAtEpochMs"]) val createdAtEpochMs: Long,
    @SerializedName(value = "image_base64", alternate = ["imageBase64", "imageData"]) val imageData: String? = null
)

data class CategorySyncPayload(
    @SerializedName("id") val id: Long = 0,
    val name: String,
    @SerializedName(value = "created_at_epoch_ms", alternate = ["createdAtEpochMs"]) val createdAtEpochMs: Long
)

data class SaleSyncPayload(
    @SerializedName("id") val id: Long,
    @SerializedName(value = "transaction_id", alternate = ["transactionId"]) val transactionId: String,
    @SerializedName(value = "cashier_id", alternate = ["cashierId"]) val cashierId: Long?,
    @SerializedName(value = "subtotal", alternate = ["sub_total"]) val subtotal: Long,
    val discount: Long,
    val tax: Long,
    val total: Long,
    @SerializedName(value = "payment_method", alternate = ["paymentMethod"]) val paymentMethod: String,
    val status: String,
    @SerializedName(value = "created_at_epoch_ms", alternate = ["createdAtEpochMs"]) val createdAtEpochMs: Long,
    @SerializedName(value = "items", alternate = ["sale_items", "saleItems"]) val items: List<SaleItemSyncPayload>? = null
)

data class SaleItemSyncPayload(
    @SerializedName("id") val id: Long = 0,
    @SerializedName(value = "sale_id", alternate = ["saleId"]) val saleId: Long = 0,
    @SerializedName(value = "product_id", alternate = ["productId"]) val productId: Long?,
    @SerializedName(value = "product_name", alternate = ["productName"]) val productName: String,
    @SerializedName(value = "unit_price", alternate = ["unitPrice"]) val unitPrice: Long,
    val quantity: Long,
    @SerializedName(value = "line_total", alternate = ["lineTotal"]) val lineTotal: Long
)

data class StockMovementSyncPayload(
    @SerializedName("id") val id: Long,
    @SerializedName(value = "product_id", alternate = ["productId"]) val productId: Long,
    @SerializedName(value = "user_id", alternate = ["userId"]) val userId: Long?,
    val type: String,
    @SerializedName(value = "quantity_delta", alternate = ["quantityDelta"]) val quantityDelta: Long,
    val note: String?,
    @SerializedName(value = "created_at_epoch_ms", alternate = ["createdAtEpochMs"]) val createdAtEpochMs: Long
)

data class AuditLogSyncPayload(
    @SerializedName("id") val id: Long,
    @SerializedName(value = "user_id", alternate = ["userId"]) val userId: Long?,
    val action: String,
    val entity: String,
    @SerializedName(value = "entity_id", alternate = ["entityId"]) val entityId: Long?,
    val detail: String?,
    @SerializedName(value = "created_at_epoch_ms", alternate = ["createdAtEpochMs"]) val createdAtEpochMs: Long
)

data class PromoSyncPayload(
    @SerializedName("id") val id: Long,
    val code: String,
    val name: String,
    val description: String?,
    @SerializedName(value = "discount_percent", alternate = ["discountPercent"]) val discountPercent: Double,
    @SerializedName(value = "valid_until_epoch_ms", alternate = ["validUntilEpochMs"]) val validUntilEpochMs: Long,
    @SerializedName(value = "is_active", alternate = ["isActive"]) val isActive: Int
)

data class SettingsSyncPayload(
    @SerializedName(value = "koperasi_name", alternate = ["koperasiName"]) val koperasiName: String,
    @SerializedName(value = "koperasi_address", alternate = ["koperasiAddress"]) val koperasiAddress: String,
    @SerializedName(value = "koperasi_phone", alternate = ["koperasiPhone"]) val koperasiPhone: String,
    @SerializedName(value = "tax_percent", alternate = ["taxPercent"]) val taxPercent: Double,
    @SerializedName(value = "discount_percent", alternate = ["discountPercent"]) val discountPercent: Double,
    @SerializedName(value = "shu_parameter", alternate = ["shuParameter"]) val shuParameter: Double,
    val latitude: Double?,
    val longitude: Double?,
    @SerializedName(value = "updated_at_epoch_ms", alternate = ["updatedAtEpochMs"]) val updatedAtEpochMs: Long
)
