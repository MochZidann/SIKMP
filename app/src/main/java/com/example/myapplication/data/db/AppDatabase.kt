package com.example.myapplication.data.db

import android.content.Context

class AppDatabase private constructor(context: Context) {
    private val helper = KoperasiDbHelper(context)

    private val userDao: UserDao = UserDaoImpl(helper)
    private val memberDao: MemberDao = MemberDaoImpl(helper)
    private val productDao: ProductDao = ProductDaoImpl(helper)
    private val stockMovementDao: StockMovementDao = StockMovementDaoImpl(helper)
    private val salesDao: SalesDao = SalesDaoImpl(helper)
    private val settingsDao: SettingsDao = SettingsDaoImpl(helper)
    private val auditLogDao: AuditLogDao = AuditLogDaoImpl(helper)
    private val promoDao: PromoDao = PromoDaoImpl(helper)

    fun userDao(): UserDao = userDao
    fun memberDao(): MemberDao = memberDao
    fun productDao(): ProductDao = productDao
    fun stockMovementDao(): StockMovementDao = stockMovementDao
    fun salesDao(): SalesDao = salesDao
    fun settingsDao(): SettingsDao = settingsDao
    fun auditLogDao(): AuditLogDao = auditLogDao
    fun promoDao(): PromoDao = promoDao

    fun <T> withTransaction(block: () -> T): T {
        val db = helper.writableDatabase
        db.beginTransaction()
        return try {
            val result = block()
            db.setTransactionSuccessful()
            result
        } finally {
            db.endTransaction()
        }
    }

    fun close() {
        helper.close()
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun closeInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
