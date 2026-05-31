package com.kopdes.kopdesjajar.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path

interface ApiService {

    @POST("sync/users")
    suspend fun syncUsers(@Body data: List<UserSyncPayload>): Response<SyncResponse>

    @POST("sync/members")
    suspend fun syncMembers(@Body data: List<MemberSyncPayload>): Response<SyncResponse>

    @POST("sync/products")
    suspend fun syncProducts(@Body data: List<ProductSyncPayload>): Response<SyncResponse>

    @POST("sync/categories")
    suspend fun syncCategories(@Body data: List<CategorySyncPayload>): Response<SyncResponse>

    @POST("sync/movements")
    suspend fun syncStockMovements(@Body data: List<StockMovementSyncPayload>): Response<SyncResponse>

    @POST("sync/sales")
    suspend fun syncSales(@Body data: List<SaleSyncPayload>): Response<SyncResponse>

    @POST("sync/settings")
    suspend fun syncSettings(@Body data: SettingsSyncPayload): Response<SyncResponse>

    @POST("sync/audit")
    suspend fun syncAuditLogs(@Body data: List<AuditLogSyncPayload>): Response<SyncResponse>

    @POST("sync/promos")
    suspend fun syncPromos(@Body data: List<PromoSyncPayload>): Response<SyncResponse>

    @DELETE("sync/users/{username}")
    suspend fun deleteUser(@Path("username") username: String): Response<SyncResponse>

    @DELETE("sync/members/{memberNo}")
    suspend fun deleteMember(@Path("memberNo") memberNo: String): Response<SyncResponse>

    @DELETE("sync/products/{barcode_or_name}")
    suspend fun deleteProduct(@Path("barcode_or_name") barcodeOrName: String): Response<SyncResponse>

    @DELETE("sync/promos/{code}")
    suspend fun deletePromo(@Path("code") code: String): Response<SyncResponse>

    @DELETE("sync/categories/{name}")
    suspend fun deleteCategory(@Path("name") name: String): Response<SyncResponse>

    // PULL Endpoints from Laravel MySQL
    @GET("sync/users")
    suspend fun pullUsers(): Response<List<UserSyncPayload>>

    @GET("sync/members")
    suspend fun pullMembers(): Response<List<MemberSyncPayload>>

    @GET("sync/products")
    suspend fun pullProducts(): Response<List<ProductSyncPayload>>

    @GET("sync/movements")
    suspend fun pullStockMovements(): Response<List<StockMovementSyncPayload>>

    @GET("sync/sales")
    suspend fun pullSales(): Response<List<SaleSyncPayload>>

    @GET("sync/settings")
    suspend fun pullSettings(): Response<List<SettingsSyncPayload>>

    @GET("sync/audit")
    suspend fun pullAuditLogs(): Response<List<AuditLogSyncPayload>>

    @GET("sync/promos")
    suspend fun pullPromos(): Response<List<PromoSyncPayload>>
}
