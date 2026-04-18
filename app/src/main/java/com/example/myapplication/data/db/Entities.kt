package com.example.myapplication.data.db

import com.example.myapplication.data.model.Role

data class UserEntity(
    val id: Long = 0,
    val name: String,
    val username: String,
    val passwordHash: String,
    val salt: String,
    val role: Role,
    val isActive: Boolean = true,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class MemberEntity(
    val id: Long = 0,
    val memberNo: String,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val isActive: Boolean = true,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class ProductEntity(
    val id: Long = 0,
    val name: String,
    val category: String,
    val price: Long,
    val stock: Long,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class CategoryEntity(
    val id: Long = 0,
    val name: String,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class StockMovementEntity(
    val id: Long = 0,
    val productId: Long,
    val userId: Long?,
    val type: String,
    val quantityDelta: Long,
    val note: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class SaleEntity(
    val id: Long = 0,
    val cashierId: Long?,
    val subtotal: Long,
    val discount: Long,
    val tax: Long,
    val total: Long,
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
    val taxPercent: Double = 0.0,
    val discountPercent: Double = 0.0,
    val shuParameter: Double = 0.0,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

data class AuditLogEntity(
    val id: Long = 0,
    val userId: Long?,
    val action: String,
    val entity: String,
    val entityId: Long?,
    val detail: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class PromoEntity(
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val discountPercent: Double,
    val validUntilEpochMs: Long,
    val isActive: Boolean = true
)
