package com.example.myapplication.data.db

import android.content.ContentValues
import android.database.Cursor
import com.example.myapplication.data.model.Role

interface UserDao {
    fun findByUsername(username: String): UserEntity?
    fun getAll(): List<UserEntity>
    fun search(query: String, limit: Int, offset: Int): List<UserEntity>
    fun count(query: String): Long
    fun insert(user: UserEntity): Long
    fun update(user: UserEntity)
    fun delete(user: UserEntity)
    fun findById(id: Long): UserEntity?
}

interface MemberDao {
    fun getAll(): List<MemberEntity>
    fun search(query: String, limit: Int, offset: Int): List<MemberEntity>
    fun count(query: String): Long
    fun findById(id: Long): MemberEntity?
    fun insert(member: MemberEntity): Long
    fun update(member: MemberEntity)
    fun delete(member: MemberEntity)
    fun countAll(): Long
}

interface ProductDao {
    fun getAll(): List<ProductEntity>
    fun findById(id: Long): ProductEntity?
    fun insert(product: ProductEntity): Long
    fun update(product: ProductEntity)
    fun updateStockAbsolute(productId: Long, stock: Long)
    fun delete(product: ProductEntity)
    fun listCategories(): List<String>
    fun listByCategoryOrderByName(category: String?): List<ProductEntity>
    fun listByCategoryOrderByStockAsc(category: String?): List<ProductEntity>
    fun countLowStock(category: String?): Long
    fun countOutOfStock(category: String?): Long
    fun totalStock(category: String?): Long
    fun totalProducts(category: String?): Long
    fun countCategories(): Long
    fun lowStockList(limit: Int): List<ProductEntity>
    fun totalAssetValue(): Long
    fun assetValueByCategory(): List<CategoryAssetRow>
    fun deadStockProducts(fromEpochMs: Long, toEpochMs: Long): List<ProductEntity>
}

interface CategoryDao {
    fun getAll(): List<CategoryEntity>
    fun findById(id: Long): CategoryEntity?
    fun findByName(name: String): CategoryEntity?
    fun insert(category: CategoryEntity): Long
    fun update(category: CategoryEntity)
    fun delete(category: CategoryEntity)
}

data class StockDailyDelta(
    val dayStartEpochMs: Long,
    val totalDelta: Long
)

data class ProductLastMovement(
    val productId: Long,
    val lastCreatedAtEpochMs: Long
)

data class StockMutationDetailRow(
    val id: Long,
    val productId: Long,
    val createdAtEpochMs: Long,
    val productName: String,
    val category: String,
    val type: String,
    val quantityDelta: Long,
    val note: String?,
    val currentStock: Long? = null
)

interface StockMovementDao {
    fun latest(limit: Int): List<StockMovementEntity>
    fun latestByProductIds(productIds: List<Long>): List<ProductLastMovement>
    fun insert(movement: StockMovementEntity): Long
    fun dailyDelta(fromEpochMs: Long, toEpochMs: Long): List<StockDailyDelta>
    fun mutationDetails(fromEpochMs: Long, toEpochMs: Long, category: String?, typeFilter: String?, limit: Int, offset: Int): List<StockMutationDetailRow>
    fun countMutationDetails(fromEpochMs: Long, toEpochMs: Long, category: String?, typeFilter: String?): Long
    fun metrics(fromEpochMs: Long, toEpochMs: Long, category: String?, typeFilter: String?): StockMutationMetrics
    fun latestWithProductName(limit: Int): List<StockActivityRow>
    fun dailyInOut(fromEpochMs: Long, toEpochMs: Long, category: String?): List<DailyInOutRow>
}

data class StockMutationMetrics(
    val totalIn: Long,
    val totalOut: Long
)

data class StockActivityRow(
    val createdAtEpochMs: Long,
    val productId: Long,
    val productName: String,
    val type: String,
    val quantityDelta: Long
)

data class DailyInOutRow(
    val dayStartEpochMs: Long,
    val totalIn: Long,
    val totalOut: Long
)

data class CategoryAssetRow(
    val category: String,
    val totalValue: Long,
    val productCount: Long
)

data class TransactionRow(
    val saleId: Long,
    val transactionId: String,
    val createdAtEpochMs: Long,
    val itemCount: Long,
    val paymentMethod: String,
    val total: Long,
    val cashierName: String
)

interface SettingsDao {
    fun get(): SettingsEntity?
    fun upsert(settings: SettingsEntity)
}

interface AuditLogDao {
    fun getAll(): List<AuditLogEntity>
    fun latest(limit: Int): List<AuditLogEntity>
    fun paged(limit: Int, offset: Int): List<AuditLogEntity>
    fun countAll(): Long
    fun between(fromEpochMs: Long, toEpochMs: Long): List<AuditLogEntity>
    fun insert(log: AuditLogEntity): Long
    fun search(query: String, fromEpochMs: Long, toEpochMs: Long): List<AuditLogEntity>
    fun searchPaged(query: String, fromEpochMs: Long, toEpochMs: Long, limit: Int, offset: Int): List<AuditLogEntity>
    fun countSearchPaged(query: String, fromEpochMs: Long, toEpochMs: Long): Long
}

interface PromoDao {
    fun getAll(): List<PromoEntity>
    fun findById(id: Long): PromoEntity?
    fun findByCode(code: String): PromoEntity?
    fun insert(promo: PromoEntity): Long
    fun update(promo: PromoEntity)
    fun delete(promo: PromoEntity)
}

data class SalesSummary(val txnCount: Long, val total: Long)

data class SaleItemCount(
    val saleId: Long,
    val itemCount: Long
)

data class SalesMetrics(
    val txnCount: Long,
    val revenue: Long,
    val itemsSold: Long
)

data class BestSeller(
    val productName: String,
    val quantity: Long
)

data class SalesDailyTotal(
    val dayStartEpochMs: Long,
    val total: Long
)

data class SaleItemDetailRow(
    val createdAtEpochMs: Long,
    val productName: String,
    val category: String,
    val quantity: Long,
    val lineTotal: Long
)

interface SalesDao {
    fun insertSaleWithItems(sale: SaleEntity, items: List<SaleItemEntity>): Long
    fun summary(fromEpochMs: Long, toEpochMs: Long): SalesSummary
    fun findSaleById(saleId: Long): SaleEntity?
    fun listSalesBetween(fromEpochMs: Long, toEpochMs: Long): List<SaleEntity>
    fun listSalesBetweenPaged(fromEpochMs: Long, toEpochMs: Long, limit: Int, offset: Int): List<SaleEntity>
    fun countSalesBetween(fromEpochMs: Long, toEpochMs: Long): Long
    fun listItemsBySaleId(saleId: Long): List<SaleItemEntity>
    fun itemCountBySaleIds(saleIds: List<Long>): List<SaleItemCount>
    fun latestWithCashier(limit: Int): List<LatestSaleWithCashier>
    fun metrics(fromEpochMs: Long, toEpochMs: Long, category: String?): SalesMetrics
    fun bestSeller(fromEpochMs: Long, toEpochMs: Long, category: String?): BestSeller?
    fun dailyTotals(fromEpochMs: Long, toEpochMs: Long, category: String?): List<SalesDailyTotal>
    fun saleItemDetails(fromEpochMs: Long, toEpochMs: Long, category: String?, limit: Int, offset: Int): List<SaleItemDetailRow>
    fun countSalesBefore(saleId: Long, startOfDayMs: Long): Long
    fun countSalesToday(startOfDayMs: Long): Long
    fun avgBasketSize(fromEpochMs: Long, toEpochMs: Long): Long
    fun top5Products(fromEpochMs: Long, toEpochMs: Long): List<BestSeller>
    fun transactionList(fromEpochMs: Long, toEpochMs: Long, limit: Int, offset: Int): List<TransactionRow>
    fun countTransactionList(fromEpochMs: Long, toEpochMs: Long): Long
}

data class LatestSaleWithCashier(
    val saleId: Long,
    val createdAtEpochMs: Long,
    val total: Long,
    val transactionId: String,
    val cashierName: String
)

internal class UserDaoImpl(private val helper: KoperasiDbHelper) : UserDao {
    override fun findByUsername(username: String): UserEntity? {
        val db = helper.readableDatabase
        db.rawQuery("SELECT * FROM users WHERE username = ? LIMIT 1", arrayOf(username)).use { c ->
            return if (c.moveToFirst()) c.toUser() else null
        }
    }

    override fun getAll(): List<UserEntity> {
        val db = helper.readableDatabase
        db.rawQuery("SELECT * FROM users ORDER BY name ASC", null).use { c ->
            return c.toList { it.toUser() }
        }
    }

    override fun search(query: String, limit: Int, offset: Int): List<UserEntity> {
        val db = helper.readableDatabase
        val q = "%$query%"
        db.rawQuery("SELECT * FROM users WHERE name LIKE ? OR username LIKE ? ORDER BY name ASC LIMIT ? OFFSET ?", arrayOf(q, q, limit.toString(), offset.toString())).use { c ->
            return c.toList { it.toUser() }
        }
    }

    override fun count(query: String): Long {
        val db = helper.readableDatabase
        val q = "%$query%"
        db.rawQuery("SELECT COUNT(*) as c FROM users WHERE name LIKE ? OR username LIKE ?", arrayOf(q, q)).use { c ->
            return if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("c")) else 0L
        }
    }

    override fun insert(user: UserEntity): Long {
        val db = helper.writableDatabase
        val cv = ContentValues().apply {
            put("name", user.name)
            put("username", user.username)
            put("passwordHash", user.passwordHash)
            put("salt", user.salt)
            put("role", user.role.name)
            put("isActive", if (user.isActive) 1 else 0)
            put("needsPasswordReset", if (user.needsPasswordReset) 1 else 0)
            put("createdAtEpochMs", user.createdAtEpochMs)
        }
        return db.insertOrThrow("users", null, cv)
    }

    override fun update(user: UserEntity) {
        val db = helper.writableDatabase
        val cv = ContentValues().apply {
            put("name", user.name)
            put("passwordHash", user.passwordHash)
            put("salt", user.salt)
            put("role", user.role.name)
            put("isActive", if (user.isActive) 1 else 0)
            put("needsPasswordReset", if (user.needsPasswordReset) 1 else 0)
        }
        db.update("users", cv, "id = ?", arrayOf(user.id.toString()))
    }

    override fun delete(user: UserEntity) {
        helper.writableDatabase.delete("users", "id = ?", arrayOf(user.id.toString()))
    }

    override fun findById(id: Long): UserEntity? {
        val db = helper.readableDatabase
        db.rawQuery("SELECT * FROM users WHERE id = ? LIMIT 1", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) c.toUser() else null
        }
    }
}

internal class MemberDaoImpl(private val helper: KoperasiDbHelper) : MemberDao {
    override fun getAll(): List<MemberEntity> {
        val db = helper.readableDatabase
        db.rawQuery("SELECT * FROM members ORDER BY name ASC", null).use { c ->
            return c.toList { it.toMember() }
        }
    }

    override fun search(query: String, limit: Int, offset: Int): List<MemberEntity> {
        val db = helper.readableDatabase
        val q = "%$query%"
        db.rawQuery("SELECT * FROM members WHERE name LIKE ? OR memberNo LIKE ? ORDER BY name ASC LIMIT ? OFFSET ?", arrayOf(q, q, limit.toString(), offset.toString())).use { c ->
            return c.toList { it.toMember() }
        }
    }

    override fun count(query: String): Long {
        val db = helper.readableDatabase
        val q = "%$query%"
        db.rawQuery("SELECT COUNT(*) as c FROM members WHERE name LIKE ? OR memberNo LIKE ?", arrayOf(q, q)).use { c ->
            return if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("c")) else 0L
        }
    }

    override fun findById(id: Long): MemberEntity? {
        val db = helper.readableDatabase
        db.rawQuery("SELECT * FROM members WHERE id = ? LIMIT 1", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) c.toMember() else null
        }
    }

    override fun insert(member: MemberEntity): Long {
        val db = helper.writableDatabase
        val cv = ContentValues().apply {
            put("memberNo", member.memberNo)
            put("name", member.name)
            put("phone", member.phone)
            put("address", member.address)
            put("isActive", if (member.isActive) 1 else 0)
            put("createdAtEpochMs", member.createdAtEpochMs)
        }
        return db.insertOrThrow("members", null, cv)
    }

    override fun update(member: MemberEntity) {
        val db = helper.writableDatabase
        val cv = ContentValues().apply {
            put("memberNo", member.memberNo)
            put("name", member.name)
            put("phone", member.phone)
            put("address", member.address)
            put("isActive", if (member.isActive) 1 else 0)
        }
        db.update("members", cv, "id = ?", arrayOf(member.id.toString()))
    }

    override fun delete(member: MemberEntity) {
        helper.writableDatabase.delete("members", "id = ?", arrayOf(member.id.toString()))
    }

    override fun countAll(): Long {
        val db = helper.readableDatabase
        db.rawQuery("SELECT COUNT(*) as c FROM members", null).use { c ->
            return if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("c")) else 0L
        }
    }
}

internal class ProductDaoImpl(private val helper: KoperasiDbHelper) : ProductDao {
    override fun getAll(): List<ProductEntity> {
        val db = helper.readableDatabase
        db.rawQuery("SELECT * FROM products ORDER BY name ASC", null).use { c ->
            return c.toList { it.toProduct() }
        }
    }

    override fun findById(id: Long): ProductEntity? {
        val db = helper.readableDatabase
        db.rawQuery("SELECT * FROM products WHERE id = ? LIMIT 1", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) c.toProduct() else null
        }
    }

    override fun insert(product: ProductEntity): Long {
        val db = helper.writableDatabase
        val cv = ContentValues().apply {
            put("barcode", product.barcode)
            put("name", product.name)
            put("category", product.category)
            put("price", product.price)
            put("purchasePrice", product.purchasePrice)
            put("stock", product.stock)
            put("minimumStock", product.minimumStock)
            put("expiredDateEpochMs", product.expiredDateEpochMs)
            put("imagePath", product.imagePath)
            put("createdAtEpochMs", product.createdAtEpochMs)
        }
        return db.insertOrThrow("products", null, cv)
    }

    override fun update(product: ProductEntity) {
        val db = helper.writableDatabase
        val cv = ContentValues().apply {
            put("barcode", product.barcode)
            put("name", product.name)
            put("category", product.category)
            put("price", product.price)
            put("stock", product.stock)
            put("minimumStock", product.minimumStock)
            put("expiredDateEpochMs", product.expiredDateEpochMs)
            put("imagePath", product.imagePath)
            put("purchasePrice", product.purchasePrice)
        }
        db.update("products", cv, "id = ?", arrayOf(product.id.toString()))
    }

    override fun updateStockAbsolute(productId: Long, stock: Long) {
        val db = helper.writableDatabase
        db.execSQL(
            "UPDATE products SET stock = ? WHERE id = ?",
            arrayOf(stock, productId)
        )
    }

    override fun delete(product: ProductEntity) {
        helper.writableDatabase.delete("products", "id = ?", arrayOf(product.id.toString()))
    }

    override fun listCategories(): List<String> {
        val db = helper.readableDatabase
        db.rawQuery("SELECT DISTINCT category FROM products ORDER BY category ASC", null).use { c ->
            val list = ArrayList<String>(c.count.coerceAtLeast(0))
            while (c.moveToNext()) {
                val value = c.getString(c.getColumnIndexOrThrow("category"))
                if (!value.isNullOrBlank()) list.add(value)
            }
            return list
        }
    }

    override fun listByCategoryOrderByName(category: String?): List<ProductEntity> {
        val db = helper.readableDatabase
        return if (category.isNullOrBlank()) {
            db.rawQuery("SELECT * FROM products ORDER BY name ASC", null).use { c -> c.toList { it.toProduct() } }
        } else {
            db.rawQuery("SELECT * FROM products WHERE category = ? ORDER BY name ASC", arrayOf(category)).use { c ->
                c.toList { it.toProduct() }
            }
        }
    }

    override fun listByCategoryOrderByStockAsc(category: String?): List<ProductEntity> {
        val db = helper.readableDatabase
        return if (category.isNullOrBlank()) {
            db.rawQuery("SELECT * FROM products ORDER BY stock ASC, name ASC", null).use { c -> c.toList { it.toProduct() } }
        } else {
            db.rawQuery("SELECT * FROM products WHERE category = ? ORDER BY stock ASC, name ASC", arrayOf(category)).use { c ->
                c.toList { it.toProduct() }
            }
        }
    }

    override fun countLowStock(category: String?): Long {
        val db = helper.readableDatabase
        val sql = if (category.isNullOrBlank()) {
            "SELECT COUNT(*) as c FROM products WHERE stock <= minimumStock"
        } else {
            "SELECT COUNT(*) as c FROM products WHERE stock <= minimumStock AND category = ?"
        }
        val args = if (category.isNullOrBlank()) null else arrayOf(category)
        db.rawQuery(sql, args).use { c ->
            return if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("c")) else 0L
        }
    }

    override fun countOutOfStock(category: String?): Long {
        val db = helper.readableDatabase
        val sql = if (category.isNullOrBlank()) {
            "SELECT COUNT(*) as c FROM products WHERE stock = 0"
        } else {
            "SELECT COUNT(*) as c FROM products WHERE stock = 0 AND category = ?"
        }
        val args = if (category.isNullOrBlank()) null else arrayOf(category)
        db.rawQuery(sql, args).use { c ->
            return if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("c")) else 0L
        }
    }

    override fun totalStock(category: String?): Long {
        val db = helper.readableDatabase
        val sql = if (category.isNullOrBlank()) {
            "SELECT COALESCE(SUM(stock), 0) as s FROM products"
        } else {
            "SELECT COALESCE(SUM(stock), 0) as s FROM products WHERE category = ?"
        }
        val args = if (category.isNullOrBlank()) null else arrayOf(category)
        db.rawQuery(sql, args).use { c ->
            return if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("s")) else 0L
        }
    }

    override fun totalProducts(category: String?): Long {
        val db = helper.readableDatabase
        val sql = if (category.isNullOrBlank()) {
            "SELECT COUNT(*) as c FROM products"
        } else {
            "SELECT COUNT(*) as c FROM products WHERE category = ?"
        }
        val args = if (category.isNullOrBlank()) null else arrayOf(category)
        db.rawQuery(sql, args).use { c ->
            return if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("c")) else 0L
        }
    }

    override fun countCategories(): Long {
        val db = helper.readableDatabase
        db.rawQuery("SELECT COUNT(DISTINCT category) as c FROM products WHERE category IS NOT NULL AND category != ''", null).use { c ->
            return if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("c")) else 0L
        }
    }

    override fun lowStockList(limit: Int): List<ProductEntity> {
        val db = helper.readableDatabase
        db.rawQuery(
            "SELECT * FROM products WHERE stock <= minimumStock ORDER BY stock ASC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c -> return c.toList { it.toProduct() } }
    }

    override fun totalAssetValue(): Long {
        val db = helper.readableDatabase
        db.rawQuery("SELECT COALESCE(SUM(stock * price), 0) as total FROM products", null).use { c ->
            return if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("total")) else 0L
        }
    }

    override fun assetValueByCategory(): List<CategoryAssetRow> {
        val db = helper.readableDatabase
        db.rawQuery(
            "SELECT category, COALESCE(SUM(stock * price), 0) as totalValue, COUNT(*) as productCount FROM products GROUP BY category ORDER BY totalValue DESC",
            null
        ).use { c ->
            return c.toList {
                CategoryAssetRow(
                    category = it.getString(it.getColumnIndexOrThrow("category")),
                    totalValue = it.getLong(it.getColumnIndexOrThrow("totalValue")),
                    productCount = it.getLong(it.getColumnIndexOrThrow("productCount"))
                )
            }
        }
    }

    override fun deadStockProducts(fromEpochMs: Long, toEpochMs: Long): List<ProductEntity> {
        val db = helper.readableDatabase
        db.rawQuery("""
            SELECT p.* FROM products p
            WHERE p.stock > 0
            AND p.id NOT IN (
                SELECT DISTINCT si.productId FROM sale_items si
                INNER JOIN sales s ON s.id = si.saleId
                WHERE s.createdAtEpochMs BETWEEN ? AND ?
                AND si.productId IS NOT NULL
            )
            ORDER BY p.name ASC
        """.trimIndent(), arrayOf(fromEpochMs.toString(), toEpochMs.toString())).use { c ->
            return c.toList { it.toProduct() }
        }
    }
}

internal class CategoryDaoImpl(private val helper: KoperasiDbHelper) : CategoryDao {
    override fun getAll(): List<CategoryEntity> {
        val db = helper.readableDatabase
        db.rawQuery("SELECT * FROM categories ORDER BY name ASC", null).use { c ->
            return c.toList { it.toCategory() }
        }
    }

    override fun findById(id: Long): CategoryEntity? {
        val db = helper.readableDatabase
        db.rawQuery("SELECT * FROM categories WHERE id = ? LIMIT 1", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) c.toCategory() else null
        }
    }

    override fun findByName(name: String): CategoryEntity? {
        val db = helper.readableDatabase
        db.rawQuery("SELECT * FROM categories WHERE name = ? LIMIT 1", arrayOf(name)).use { c ->
            return if (c.moveToFirst()) c.toCategory() else null
        }
    }

    override fun insert(category: CategoryEntity): Long {
        val db = helper.writableDatabase
        val cv = ContentValues().apply {
            put("name", category.name)
            put("createdAtEpochMs", category.createdAtEpochMs)
        }
        return db.insertOrThrow("categories", null, cv)
    }

    override fun update(category: CategoryEntity) {
        val db = helper.writableDatabase
        val cv = ContentValues().apply {
            put("name", category.name)
        }
        db.update("categories", cv, "id = ?", arrayOf(category.id.toString()))
    }

    override fun delete(category: CategoryEntity) {
        helper.writableDatabase.delete("categories", "id = ?", arrayOf(category.id.toString()))
    }
}

internal class StockMovementDaoImpl(private val helper: KoperasiDbHelper) : StockMovementDao {
    override fun latest(limit: Int): List<StockMovementEntity> {
        val db = helper.readableDatabase
        db.rawQuery("SELECT * FROM stock_movements ORDER BY createdAtEpochMs DESC LIMIT $limit", null).use { c ->
            return c.toList { it.toStockMovement() }
        }
    }

    override fun latestByProductIds(productIds: List<Long>): List<ProductLastMovement> {
        if (productIds.isEmpty()) return emptyList()
        val placeholders = productIds.joinToString(",") { "?" }
        val args = productIds.map { it.toString() }.toTypedArray()
        val db = helper.readableDatabase
        db.rawQuery(
            "SELECT productId, MAX(createdAtEpochMs) as lastCreatedAtEpochMs FROM stock_movements WHERE productId IN ($placeholders) GROUP BY productId",
            args
        ).use { c ->
            val list = ArrayList<ProductLastMovement>(c.count.coerceAtLeast(0))
            while (c.moveToNext()) {
                val productId = c.getLong(c.getColumnIndexOrThrow("productId"))
                val last = c.getLong(c.getColumnIndexOrThrow("lastCreatedAtEpochMs"))
                list.add(ProductLastMovement(productId = productId, lastCreatedAtEpochMs = last))
            }
            return list
        }
    }

    override fun insert(movement: StockMovementEntity): Long {
        val db = helper.writableDatabase
        val cv = ContentValues().apply {
            put("productId", movement.productId)
            put("userId", movement.userId)
            put("type", movement.type)
            put("quantityDelta", movement.quantityDelta)
            put("note", movement.note)
            put("createdAtEpochMs", movement.createdAtEpochMs)
        }
        return db.insertOrThrow("stock_movements", null, cv)
    }

    override fun dailyDelta(fromEpochMs: Long, toEpochMs: Long): List<StockDailyDelta> {
        val db = helper.readableDatabase
        val dayMs = 86_400_000L
        db.rawQuery(
            "SELECT (createdAtEpochMs / $dayMs) as dayKey, COALESCE(SUM(quantityDelta), 0) as totalDelta FROM stock_movements WHERE createdAtEpochMs BETWEEN ? AND ? GROUP BY dayKey ORDER BY dayKey ASC",
            arrayOf(fromEpochMs.toString(), toEpochMs.toString())
        ).use { c ->
            val list = ArrayList<StockDailyDelta>(c.count.coerceAtLeast(0))
            while (c.moveToNext()) {
                val dayKey = c.getLong(c.getColumnIndexOrThrow("dayKey"))
                val total = c.getLong(c.getColumnIndexOrThrow("totalDelta"))
                list.add(StockDailyDelta(dayStartEpochMs = dayKey * dayMs, totalDelta = total))
            }
            return list
        }
    }

    override fun mutationDetails(fromEpochMs: Long, toEpochMs: Long, category: String?, typeFilter: String?, limit: Int, offset: Int): List<StockMutationDetailRow> {
        val db = helper.readableDatabase
        val typeClause = when (typeFilter) {
            "POSITIVE" -> "AND sm.quantityDelta > 0"
            "NEGATIVE" -> "AND sm.quantityDelta < 0"
            else -> ""
        }
        val sql = """
            SELECT sm.*, p.name as productName, p.category as category, p.stock as currentStock
            FROM stock_movements sm
            INNER JOIN products p ON p.id = sm.productId
            WHERE sm.createdAtEpochMs BETWEEN ? AND ?
            ${if (category != null) "AND p.category = ?" else ""}
            $typeClause
            ORDER BY sm.createdAtEpochMs DESC
            LIMIT ? OFFSET ?
        """.trimIndent()
        
        val argsList = mutableListOf(fromEpochMs.toString(), toEpochMs.toString())
        if (category != null) argsList.add(category)
        argsList.add(limit.toString())
        argsList.add(offset.toString())
        
        db.rawQuery(sql, argsList.toTypedArray()).use { c ->
            return c.toList { 
                StockMutationDetailRow(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    productId = it.getLong(it.getColumnIndexOrThrow("productId")),
                    createdAtEpochMs = it.getLong(it.getColumnIndexOrThrow("createdAtEpochMs")),
                    productName = it.getString(it.getColumnIndexOrThrow("productName")),
                    category = it.getString(it.getColumnIndexOrThrow("category")),
                    type = it.getString(it.getColumnIndexOrThrow("type")),
                    quantityDelta = it.getLong(it.getColumnIndexOrThrow("quantityDelta")),
                    note = it.getStringOrNull("note"),
                    currentStock = it.getLong(it.getColumnIndexOrThrow("currentStock"))
                )
            }
        }
    }

    override fun countMutationDetails(fromEpochMs: Long, toEpochMs: Long, category: String?, typeFilter: String?): Long {
        val db = helper.readableDatabase
        val typeClause = when (typeFilter) {
            "POSITIVE" -> "AND sm.quantityDelta > 0"
            "NEGATIVE" -> "AND sm.quantityDelta < 0"
            else -> ""
        }
        val sql = """
            SELECT COUNT(*) as c
            FROM stock_movements sm
            INNER JOIN products p ON p.id = sm.productId
            WHERE sm.createdAtEpochMs BETWEEN ? AND ?
            ${if (category != null) "AND p.category = ?" else ""}
            $typeClause
        """.trimIndent()
        val argsList = mutableListOf(fromEpochMs.toString(), toEpochMs.toString())
        if (category != null) argsList.add(category)
        db.rawQuery(sql, argsList.toTypedArray()).use { c ->
            return if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("c")) else 0L
        }
    }

    override fun metrics(fromEpochMs: Long, toEpochMs: Long, category: String?, typeFilter: String?): StockMutationMetrics {
        val db = helper.readableDatabase
        val catFilter = if (category != null) "AND p.category = ?" else ""
        
        fun getSum(operator: String): Long {
            val sql = """
                SELECT COALESCE(SUM(sm.quantityDelta), 0) as total
                FROM stock_movements sm
                INNER JOIN products p ON p.id = sm.productId
                WHERE sm.createdAtEpochMs BETWEEN ? AND ?
                $catFilter
                AND sm.quantityDelta $operator 0
            """.trimIndent()
            val args = if (category == null) arrayOf(fromEpochMs.toString(), toEpochMs.toString()) 
                       else arrayOf(fromEpochMs.toString(), toEpochMs.toString(), category)
            db.rawQuery(sql, args).use { c ->
                return if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("total")) else 0L
            }
        }
        
        val totalIn = if (typeFilter == null || typeFilter == "POSITIVE") getSum(">") else 0L
        val totalOut = if (typeFilter == null || typeFilter == "NEGATIVE") getSum("<") else 0L
        
        return StockMutationMetrics(totalIn = totalIn, totalOut = if (totalOut < 0) -totalOut else totalOut)
    }

    override fun latestWithProductName(limit: Int): List<StockActivityRow> {
        val db = helper.readableDatabase
        db.rawQuery("""
            SELECT sm.createdAtEpochMs, sm.productId, p.name as productName, sm.type, sm.quantityDelta
            FROM stock_movements sm
            INNER JOIN products p ON p.id = sm.productId
            ORDER BY sm.createdAtEpochMs DESC
            LIMIT ?
        """.trimIndent(), arrayOf(limit.toString())).use { c ->
            return c.toList {
                StockActivityRow(
                    createdAtEpochMs = it.getLong(it.getColumnIndexOrThrow("createdAtEpochMs")),
                    productId = it.getLong(it.getColumnIndexOrThrow("productId")),
                    productName = it.getString(it.getColumnIndexOrThrow("productName")),
                    type = it.getString(it.getColumnIndexOrThrow("type")),
                    quantityDelta = it.getLong(it.getColumnIndexOrThrow("quantityDelta"))
                )
            }
        }
    }

    override fun dailyInOut(fromEpochMs: Long, toEpochMs: Long, category: String?): List<DailyInOutRow> {
        val db = helper.readableDatabase
        val dayMs = 86_400_000L
        val catFilter = if (category != null) "AND p.category = ?" else ""
        val sql = """
            SELECT (sm.createdAtEpochMs / $dayMs) as dayKey,
                   COALESCE(SUM(CASE WHEN sm.quantityDelta > 0 THEN sm.quantityDelta ELSE 0 END), 0) as totalIn,
                   COALESCE(SUM(CASE WHEN sm.quantityDelta < 0 THEN -sm.quantityDelta ELSE 0 END), 0) as totalOut
            FROM stock_movements sm
            INNER JOIN products p ON p.id = sm.productId
            WHERE sm.createdAtEpochMs BETWEEN ? AND ?
            $catFilter
            GROUP BY dayKey ORDER BY dayKey ASC
        """.trimIndent()
        val args = if (category == null) arrayOf(fromEpochMs.toString(), toEpochMs.toString())
                   else arrayOf(fromEpochMs.toString(), toEpochMs.toString(), category)
        db.rawQuery(sql, args).use { c ->
            return c.toList {
                DailyInOutRow(
                    dayStartEpochMs = it.getLong(it.getColumnIndexOrThrow("dayKey")) * dayMs,
                    totalIn = it.getLong(it.getColumnIndexOrThrow("totalIn")),
                    totalOut = it.getLong(it.getColumnIndexOrThrow("totalOut"))
                )
            }
        }
    }
}

internal class SettingsDaoImpl(private val helper: KoperasiDbHelper) : SettingsDao {
    override fun get(): SettingsEntity? {
        val db = helper.readableDatabase
        db.rawQuery("SELECT * FROM settings WHERE id = 1 LIMIT 1", null).use { c ->
            return if (c.moveToFirst()) c.toSettings() else null
        }
    }

    override fun upsert(settings: SettingsEntity) {
        val db = helper.writableDatabase
        val cv = ContentValues().apply {
            put("id", 1)
            put("koperasiName", settings.koperasiName)
            put("koperasiAddress", settings.koperasiAddress)
            put("taxPercent", settings.taxPercent)
            put("discountPercent", settings.discountPercent)
            put("shuParameter", settings.shuParameter)
            put("updatedAtEpochMs", System.currentTimeMillis())
        }
        db.insertWithOnConflict("settings", null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }
}

internal class AuditLogDaoImpl(private val helper: KoperasiDbHelper) : AuditLogDao {
    override fun getAll(): List<AuditLogEntity> {
        val db = helper.readableDatabase
        db.rawQuery("SELECT * FROM audit_logs ORDER BY createdAtEpochMs DESC", null).use { c ->
            return c.toList { it.toAudit() }
        }
    }

    override fun latest(limit: Int): List<AuditLogEntity> {
        val db = helper.readableDatabase
        db.rawQuery("SELECT * FROM audit_logs ORDER BY createdAtEpochMs DESC LIMIT $limit", null).use { c ->
            return c.toList { it.toAudit() }
        }
    }

    override fun paged(limit: Int, offset: Int): List<AuditLogEntity> {
        val db = helper.readableDatabase
        db.rawQuery("SELECT * FROM audit_logs ORDER BY createdAtEpochMs DESC LIMIT ? OFFSET ?", arrayOf(limit.toString(), offset.toString())).use { c ->
            return c.toList { it.toAudit() }
        }
    }

    override fun countAll(): Long {
        val db = helper.readableDatabase
        db.rawQuery("SELECT COUNT(*) as c FROM audit_logs", null).use { c ->
            return if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("c")) else 0L
        }
    }

    override fun between(fromEpochMs: Long, toEpochMs: Long): List<AuditLogEntity> {
        val db = helper.readableDatabase
        db.rawQuery(
            "SELECT * FROM audit_logs WHERE createdAtEpochMs BETWEEN ? AND ? ORDER BY createdAtEpochMs DESC",
            arrayOf(fromEpochMs.toString(), toEpochMs.toString())
        ).use { c ->
            return c.toList { it.toAudit() }
        }
    }

    override fun insert(log: AuditLogEntity): Long {
        val db = helper.writableDatabase
        val cv = ContentValues().apply {
            put("userId", log.userId)
            put("action", log.action)
            put("entity", log.entity)
            put("entityId", log.entityId)
            put("detail", log.detail)
            put("createdAtEpochMs", log.createdAtEpochMs)
        }
        return db.insertOrThrow("audit_logs", null, cv)
    }

    override fun search(query: String, fromEpochMs: Long, toEpochMs: Long): List<AuditLogEntity> {
        val db = helper.readableDatabase
        val q = "%$query%"
        val sql = "SELECT * FROM audit_logs WHERE (action LIKE ? OR entity LIKE ? OR detail LIKE ?) AND createdAtEpochMs BETWEEN ? AND ? ORDER BY createdAtEpochMs DESC"
        db.rawQuery(sql, arrayOf(q, q, q, fromEpochMs.toString(), toEpochMs.toString())).use { c ->
            return c.toList { it.toAudit() }
        }
    }

    override fun searchPaged(query: String, fromEpochMs: Long, toEpochMs: Long, limit: Int, offset: Int): List<AuditLogEntity> {
        val db = helper.readableDatabase
        val q = "%$query%"
        val sql = "SELECT * FROM audit_logs WHERE (action LIKE ? OR entity LIKE ? OR detail LIKE ?) AND createdAtEpochMs BETWEEN ? AND ? ORDER BY createdAtEpochMs DESC LIMIT ? OFFSET ?"
        db.rawQuery(sql, arrayOf(q, q, q, fromEpochMs.toString(), toEpochMs.toString(), limit.toString(), offset.toString())).use { c ->
            return c.toList { it.toAudit() }
        }
    }

    override fun countSearchPaged(query: String, fromEpochMs: Long, toEpochMs: Long): Long {
        val db = helper.readableDatabase
        val q = "%$query%"
        val sql = "SELECT COUNT(*) as c FROM audit_logs WHERE (action LIKE ? OR entity LIKE ? OR detail LIKE ?) AND createdAtEpochMs BETWEEN ? AND ?"
        db.rawQuery(sql, arrayOf(q, q, q, fromEpochMs.toString(), toEpochMs.toString())).use { c ->
            return if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("c")) else 0L
        }
    }
}

internal class PromoDaoImpl(private val helper: KoperasiDbHelper) : PromoDao {
    override fun getAll(): List<PromoEntity> {
        val db = helper.readableDatabase
        db.rawQuery("SELECT * FROM promos ORDER BY name ASC", null).use { c ->
            return c.toList { it.toPromo() }
        }
    }

    override fun findById(id: Long): PromoEntity? {
        val db = helper.readableDatabase
        db.rawQuery("SELECT * FROM promos WHERE id = ? LIMIT 1", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) c.toPromo() else null
        }
    }

    override fun findByCode(code: String): PromoEntity? {
        val db = helper.readableDatabase
        db.rawQuery("SELECT * FROM promos WHERE code = ? AND isActive = 1 LIMIT 1", arrayOf(code)).use { c ->
            return if (c.moveToFirst()) c.toPromo() else null
        }
    }

    override fun insert(promo: PromoEntity): Long {
        val db = helper.writableDatabase
        val cv = ContentValues().apply {
            put("code", promo.code)
            put("name", promo.name)
            put("description", promo.description)
            put("discountPercent", promo.discountPercent)
            put("validUntilEpochMs", promo.validUntilEpochMs)
            put("isActive", if (promo.isActive) 1 else 0)
        }
        return db.insertOrThrow("promos", null, cv)
    }

    override fun update(promo: PromoEntity) {
        val db = helper.writableDatabase
        val cv = ContentValues().apply {
            put("code", promo.code)
            put("name", promo.name)
            put("description", promo.description)
            put("discountPercent", promo.discountPercent)
            put("validUntilEpochMs", promo.validUntilEpochMs)
            put("isActive", if (promo.isActive) 1 else 0)
        }
        db.update("promos", cv, "id = ?", arrayOf(promo.id.toString()))
    }

    override fun delete(promo: PromoEntity) {
        helper.writableDatabase.delete("promos", "id = ?", arrayOf(promo.id.toString()))
    }
}

internal class SalesDaoImpl(private val helper: KoperasiDbHelper) : SalesDao {
    override fun insertSaleWithItems(sale: SaleEntity, items: List<SaleItemEntity>): Long {
        val db = helper.writableDatabase
        val saleValues = ContentValues().apply {
            put("transactionId", sale.transactionId)
            put("cashierId", sale.cashierId)
            put("subtotal", sale.subtotal)
            put("discount", sale.discount)
            put("tax", sale.tax)
            put("total", sale.total)
            put("paymentMethod", sale.paymentMethod)
            put("status", sale.status)
            put("createdAtEpochMs", sale.createdAtEpochMs)
        }
        val saleId = db.insertOrThrow("sales", null, saleValues)
        for (item in items) {
            val cv = ContentValues().apply {
                put("saleId", saleId)
                put("productId", item.productId)
                put("productName", item.productName)
                put("unitPrice", item.unitPrice)
                put("quantity", item.quantity)
                put("lineTotal", item.lineTotal)
            }
            db.insertOrThrow("sale_items", null, cv)
        }
        return saleId
    }

    override fun summary(fromEpochMs: Long, toEpochMs: Long): SalesSummary {
        val db = helper.readableDatabase
        db.rawQuery(
            "SELECT COUNT(*) as txnCount, COALESCE(SUM(total), 0) as total FROM sales WHERE createdAtEpochMs BETWEEN ? AND ? AND status = 'SUCCESS'",
            arrayOf(fromEpochMs.toString(), toEpochMs.toString())
        ).use { c ->
            if (!c.moveToFirst()) return SalesSummary(0, 0)
            return SalesSummary(c.getLong(c.getColumnIndexOrThrow("txnCount")), c.getLong(c.getColumnIndexOrThrow("total")))
        }
    }

    override fun findSaleById(saleId: Long): SaleEntity? {
        val db = helper.readableDatabase
        db.rawQuery("SELECT * FROM sales WHERE id = ? LIMIT 1", arrayOf(saleId.toString())).use { c ->
            return if (c.moveToFirst()) c.toSale() else null
        }
    }

    override fun listSalesBetween(fromEpochMs: Long, toEpochMs: Long): List<SaleEntity> {
        val db = helper.readableDatabase
        db.rawQuery(
            "SELECT * FROM sales WHERE createdAtEpochMs BETWEEN ? AND ? AND status = 'SUCCESS' ORDER BY createdAtEpochMs DESC",
            arrayOf(fromEpochMs.toString(), toEpochMs.toString())
        ).use { c ->
            return c.toList { it.toSale() }
        }
    }

    override fun listSalesBetweenPaged(fromEpochMs: Long, toEpochMs: Long, limit: Int, offset: Int): List<SaleEntity> {
        val db = helper.readableDatabase
        db.rawQuery(
            "SELECT * FROM sales WHERE createdAtEpochMs BETWEEN ? AND ? AND status = 'SUCCESS' ORDER BY createdAtEpochMs DESC LIMIT ? OFFSET ?",
            arrayOf(fromEpochMs.toString(), toEpochMs.toString(), limit.toString(), offset.toString())
        ).use { c ->
            return c.toList { it.toSale() }
        }
    }

    override fun countSalesBetween(fromEpochMs: Long, toEpochMs: Long): Long {
        val db = helper.readableDatabase
        db.rawQuery(
            "SELECT COUNT(*) as c FROM sales WHERE createdAtEpochMs BETWEEN ? AND ? AND status = 'SUCCESS'",
            arrayOf(fromEpochMs.toString(), toEpochMs.toString())
        ).use { c ->
            return if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("c")) else 0L
        }
    }

    override fun listItemsBySaleId(saleId: Long): List<SaleItemEntity> {
        val db = helper.readableDatabase
        db.rawQuery(
            "SELECT * FROM sale_items WHERE saleId = ? ORDER BY id ASC",
            arrayOf(saleId.toString())
        ).use { c ->
            return c.toList { it.toSaleItem() }
        }
    }

    override fun itemCountBySaleIds(saleIds: List<Long>): List<SaleItemCount> {
        if (saleIds.isEmpty()) return emptyList()
        val placeholders = saleIds.joinToString(",") { "?" }
        val args = saleIds.map { it.toString() }.toTypedArray()
        val db = helper.readableDatabase
        db.rawQuery(
            "SELECT saleId, COALESCE(SUM(quantity), 0) as itemCount FROM sale_items WHERE saleId IN ($placeholders) GROUP BY saleId",
            args
        ).use { c ->
            return c.toList { it.toSaleItemCount() }
        }
    }

    override fun latestWithCashier(limit: Int): List<LatestSaleWithCashier> {
        val db = helper.readableDatabase
        db.rawQuery(
            """
            SELECT s.id as saleId, s.createdAtEpochMs as createdAtEpochMs, s.total as total, s.transactionId as transactionId, COALESCE(u.name, u.username, '-') as cashierName
            FROM sales s
            LEFT JOIN users u ON u.id = s.cashierId
            WHERE s.status = 'SUCCESS'
            ORDER BY s.createdAtEpochMs DESC
            LIMIT $limit
            """.trimIndent(),
            null
        ).use { c ->
            return c.toList { it.toLatestSaleWithCashier() }
        }
    }

    override fun metrics(fromEpochMs: Long, toEpochMs: Long, category: String?): SalesMetrics {
        val db = helper.readableDatabase
        val fromStr = fromEpochMs.toString()
        val toStr = toEpochMs.toString()
        
        return if (category.isNullOrBlank()) {
            db.rawQuery(
                "SELECT COUNT(*) as txnCount, COALESCE(SUM(total), 0) as revenue FROM sales WHERE createdAtEpochMs BETWEEN ? AND ? AND status = 'SUCCESS'",
                arrayOf(fromStr, toStr)
            ).use { c ->
                val txn = if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("txnCount")) else 0L
                val rev = if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("revenue")) else 0L
                val items = db.rawQuery(
                    "SELECT COALESCE(SUM(si.quantity), 0) as itemsSold FROM sale_items si INNER JOIN sales s ON s.id = si.saleId WHERE s.createdAtEpochMs BETWEEN ? AND ? AND s.status = 'SUCCESS'",
                    arrayOf(fromStr, toStr)
                ).use { c2 -> if (c2.moveToFirst()) c2.getLong(c2.getColumnIndexOrThrow("itemsSold")) else 0L }
                SalesMetrics(txnCount = txn, revenue = rev, itemsSold = items)
            }
        } else {
            val args = arrayOf(fromStr, toStr, category)
            val txn = db.rawQuery(
                "SELECT COUNT(DISTINCT s.id) as txnCount FROM sales s INNER JOIN sale_items si ON si.saleId = s.id INNER JOIN products p ON p.id = si.productId WHERE s.createdAtEpochMs BETWEEN ? AND ? AND p.category = ? AND s.status = 'SUCCESS'",
                args
            ).use { c -> if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("txnCount")) else 0L }
            val items = db.rawQuery(
                "SELECT COALESCE(SUM(si.quantity), 0) as itemsSold FROM sale_items si INNER JOIN sales s ON s.id = si.saleId INNER JOIN products p ON p.id = si.productId WHERE s.createdAtEpochMs BETWEEN ? AND ? AND p.category = ? AND s.status = 'SUCCESS'",
                args
            ).use { c -> if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("itemsSold")) else 0L }
            val revenue = db.rawQuery(
                "SELECT COALESCE(SUM(si.lineTotal), 0) as revenue FROM sale_items si INNER JOIN sales s ON s.id = si.saleId INNER JOIN products p ON p.id = si.productId WHERE s.createdAtEpochMs BETWEEN ? AND ? AND p.category = ? AND s.status = 'SUCCESS'",
                args
            ).use { c -> if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("revenue")) else 0L }
            SalesMetrics(txnCount = txn, revenue = revenue, itemsSold = items)
        }
    }

    override fun bestSeller(fromEpochMs: Long, toEpochMs: Long, category: String?): BestSeller? {
        val db = helper.readableDatabase
        val fromStr = fromEpochMs.toString()
        val toStr = toEpochMs.toString()
        return if (category.isNullOrBlank()) {
            db.rawQuery(
                "SELECT si.productName as productName, COALESCE(SUM(si.quantity), 0) as qty FROM sale_items si INNER JOIN sales s ON s.id = si.saleId WHERE s.createdAtEpochMs BETWEEN ? AND ? AND s.status = 'SUCCESS' GROUP BY si.productName ORDER BY qty DESC LIMIT 1",
                arrayOf(fromStr, toStr)
            ).use { c ->
                if (c.moveToFirst()) BestSeller(c.getString(c.getColumnIndexOrThrow("productName")), c.getLong(c.getColumnIndexOrThrow("qty"))) else null
            }
        } else {
            db.rawQuery(
                "SELECT si.productName as productName, COALESCE(SUM(si.quantity), 0) as qty FROM sale_items si INNER JOIN sales s ON s.id = si.saleId INNER JOIN products p ON p.id = si.productId WHERE s.createdAtEpochMs BETWEEN ? AND ? AND p.category = ? AND s.status = 'SUCCESS' GROUP BY si.productName ORDER BY qty DESC LIMIT 1",
                arrayOf(fromStr, toStr, category)
            ).use { c ->
                if (c.moveToFirst()) BestSeller(c.getString(c.getColumnIndexOrThrow("productName")), c.getLong(c.getColumnIndexOrThrow("qty"))) else null
            }
        }
    }

    override fun dailyTotals(fromEpochMs: Long, toEpochMs: Long, category: String?): List<SalesDailyTotal> {
        val db = helper.readableDatabase
        val dayMs = 86_400_000L
        val fromStr = fromEpochMs.toString()
        val toStr = toEpochMs.toString()
        return if (category.isNullOrBlank()) {
            db.rawQuery(
                "SELECT (createdAtEpochMs / $dayMs) as dayKey, COALESCE(SUM(total), 0) as total FROM sales WHERE createdAtEpochMs BETWEEN ? AND ? AND status = 'SUCCESS' GROUP BY dayKey ORDER BY dayKey ASC",
                arrayOf(fromStr, toStr)
            ).use { c ->
                val list = ArrayList<SalesDailyTotal>(c.count.coerceAtLeast(0))
                while (c.moveToNext()) {
                    val dayKey = c.getLong(c.getColumnIndexOrThrow("dayKey"))
                    val total = c.getLong(c.getColumnIndexOrThrow("total"))
                    list.add(SalesDailyTotal(dayStartEpochMs = dayKey * dayMs, total = total))
                }
                list
            }
        } else {
            db.rawQuery(
                "SELECT (s.createdAtEpochMs / $dayMs) as dayKey, COALESCE(SUM(si.lineTotal), 0) as total FROM sale_items si INNER JOIN sales s ON s.id = si.saleId INNER JOIN products p ON p.id = si.productId WHERE s.createdAtEpochMs BETWEEN ? AND ? AND p.category = ? AND s.status = 'SUCCESS' GROUP BY dayKey ORDER BY dayKey ASC",
                arrayOf(fromStr, toStr, category)
            ).use { c ->
                val list = ArrayList<SalesDailyTotal>(c.count.coerceAtLeast(0))
                while (c.moveToNext()) {
                    val dayKey = c.getLong(c.getColumnIndexOrThrow("dayKey"))
                    val total = c.getLong(c.getColumnIndexOrThrow("total"))
                    list.add(SalesDailyTotal(dayStartEpochMs = dayKey * dayMs, total = total))
                }
                list
            }
        }
    }

    override fun saleItemDetails(fromEpochMs: Long, toEpochMs: Long, category: String?, limit: Int, offset: Int): List<SaleItemDetailRow> {
        val db = helper.readableDatabase
        val fromStr = fromEpochMs.toString()
        val toStr = toEpochMs.toString()
        return if (category.isNullOrBlank()) {
            db.rawQuery(
                "SELECT s.createdAtEpochMs as createdAtEpochMs, si.productName as productName, COALESCE(p.category, '-') as category, si.quantity as quantity, si.lineTotal as lineTotal FROM sale_items si INNER JOIN sales s ON s.id = si.saleId LEFT JOIN products p ON p.id = si.productId WHERE s.createdAtEpochMs BETWEEN ? AND ? AND s.status = 'SUCCESS' ORDER BY s.createdAtEpochMs DESC, si.id DESC LIMIT $limit OFFSET $offset",
                arrayOf(fromStr, toStr)
            ).use { c -> c.toList { it.toSaleItemDetailRow() } }
        } else {
            db.rawQuery(
                "SELECT s.createdAtEpochMs as createdAtEpochMs, si.productName as productName, COALESCE(p.category, '-') as category, si.quantity as quantity, si.lineTotal as lineTotal FROM sale_items si INNER JOIN sales s ON s.id = si.saleId INNER JOIN products p ON p.id = si.productId WHERE s.createdAtEpochMs BETWEEN ? AND ? AND p.category = ? AND s.status = 'SUCCESS' ORDER BY s.createdAtEpochMs DESC, si.id DESC LIMIT $limit OFFSET $offset",
                arrayOf(fromStr, toStr, category)
            ).use { c -> c.toList { it.toSaleItemDetailRow() } }
        }
    }

    override fun countSalesBefore(saleId: Long, startOfDayMs: Long): Long {
        val db = helper.readableDatabase
        db.rawQuery("SELECT COUNT(*) as c FROM sales WHERE createdAtEpochMs >= ? AND id <= ? AND status = 'SUCCESS'", arrayOf(startOfDayMs.toString(), saleId.toString())).use { c ->
            return if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("c")) else 0L
        }
    }

    override fun countSalesToday(startOfDayMs: Long): Long {
        val db = helper.readableDatabase
        db.rawQuery("SELECT COUNT(*) as c FROM sales WHERE createdAtEpochMs >= ? AND status = 'SUCCESS'", arrayOf(startOfDayMs.toString())).use { c ->
            return if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("c")) else 0L
        }
    }

    override fun avgBasketSize(fromEpochMs: Long, toEpochMs: Long): Long {
        val db = helper.readableDatabase
        db.rawQuery(
            "SELECT COALESCE(AVG(total), 0) as avg FROM sales WHERE createdAtEpochMs BETWEEN ? AND ? AND status = 'SUCCESS'",
            arrayOf(fromEpochMs.toString(), toEpochMs.toString())
        ).use { c ->
            return if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("avg")) else 0L
        }
    }

    override fun top5Products(fromEpochMs: Long, toEpochMs: Long): List<BestSeller> {
        val db = helper.readableDatabase
        db.rawQuery("""
            SELECT si.productName, COALESCE(SUM(si.quantity), 0) as qty
            FROM sale_items si
            INNER JOIN sales s ON s.id = si.saleId
            WHERE s.createdAtEpochMs BETWEEN ? AND ? AND s.status = 'SUCCESS'
            GROUP BY si.productName
            ORDER BY qty DESC
            LIMIT 5
        """.trimIndent(), arrayOf(fromEpochMs.toString(), toEpochMs.toString())).use { c ->
            return c.toList { BestSeller(it.getString(it.getColumnIndexOrThrow("productName")), it.getLong(it.getColumnIndexOrThrow("qty"))) }
        }
    }

    override fun transactionList(fromEpochMs: Long, toEpochMs: Long, limit: Int, offset: Int): List<TransactionRow> {
        val db = helper.readableDatabase
        db.rawQuery("""
            SELECT s.id as saleId, s.transactionId, s.createdAtEpochMs, COUNT(si.id) as itemCount, s.paymentMethod, s.total, COALESCE(u.name, u.username, '-') as cashierName
            FROM sales s
            LEFT JOIN sale_items si ON si.saleId = s.id
            LEFT JOIN users u ON u.id = s.cashierId
            WHERE s.createdAtEpochMs BETWEEN ? AND ? AND s.status = 'SUCCESS'
            GROUP BY s.id
            ORDER BY s.createdAtEpochMs DESC
            LIMIT ? OFFSET ?
        """.trimIndent(), arrayOf(fromEpochMs.toString(), toEpochMs.toString(), limit.toString(), offset.toString())).use { c ->
            return c.toList {
                TransactionRow(
                    saleId = it.getLong(it.getColumnIndexOrThrow("saleId")),
                    transactionId = it.getString(it.getColumnIndexOrThrow("transactionId")),
                    createdAtEpochMs = it.getLong(it.getColumnIndexOrThrow("createdAtEpochMs")),
                    itemCount = it.getLong(it.getColumnIndexOrThrow("itemCount")),
                    paymentMethod = it.getString(it.getColumnIndexOrThrow("paymentMethod")),
                    total = it.getLong(it.getColumnIndexOrThrow("total")),
                    cashierName = it.getString(it.getColumnIndexOrThrow("cashierName"))
                )
            }
        }
    }

    override fun countTransactionList(fromEpochMs: Long, toEpochMs: Long): Long {
        val db = helper.readableDatabase
        db.rawQuery("SELECT COUNT(*) as c FROM sales WHERE createdAtEpochMs BETWEEN ? AND ? AND status = 'SUCCESS'", 
            arrayOf(fromEpochMs.toString(), toEpochMs.toString())).use { c ->
            return if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("c")) else 0L
        }
    }
}

private fun Cursor.toUser(): UserEntity {
    return UserEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        username = getString(getColumnIndexOrThrow("username")),
        passwordHash = getString(getColumnIndexOrThrow("passwordHash")),
        salt = getString(getColumnIndexOrThrow("salt")),
        role = Role.valueOf(getString(getColumnIndexOrThrow("role"))),
        isActive = getInt(getColumnIndexOrThrow("isActive")) == 1,
        needsPasswordReset = getInt(getColumnIndexOrThrow("needsPasswordReset")) == 1,
        createdAtEpochMs = getLong(getColumnIndexOrThrow("createdAtEpochMs"))
    )
}

private fun Cursor.toMember(): MemberEntity {
    return MemberEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        memberNo = getString(getColumnIndexOrThrow("memberNo")),
        name = getString(getColumnIndexOrThrow("name")),
        phone = getStringOrNull("phone"),
        address = getStringOrNull("address"),
        isActive = getInt(getColumnIndexOrThrow("isActive")) == 1,
        createdAtEpochMs = getLong(getColumnIndexOrThrow("createdAtEpochMs"))
    )
}

private fun Cursor.toProduct(): ProductEntity {
    return ProductEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        barcode = getStringOrNull("barcode"),
        name = getString(getColumnIndexOrThrow("name")),
        category = getString(getColumnIndexOrThrow("category")),
        price = getLong(getColumnIndexOrThrow("price")),
        purchasePrice = getLong(getColumnIndexOrThrow("purchasePrice")),
        stock = getLong(getColumnIndexOrThrow("stock")),
        minimumStock = getLong(getColumnIndexOrThrow("minimumStock")),
        expiredDateEpochMs = getLongOrNull("expiredDateEpochMs"),
        imagePath = getStringOrNull("imagePath"),
        createdAtEpochMs = getLong(getColumnIndexOrThrow("createdAtEpochMs"))
    )
}

private fun Cursor.toCategory(): CategoryEntity {
    return CategoryEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        createdAtEpochMs = getLong(getColumnIndexOrThrow("createdAtEpochMs"))
    )
}

private fun Cursor.toStockMovement(): StockMovementEntity {
    return StockMovementEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        productId = getLong(getColumnIndexOrThrow("productId")),
        userId = getLongOrNull("userId"),
        type = getString(getColumnIndexOrThrow("type")),
        quantityDelta = getLong(getColumnIndexOrThrow("quantityDelta")),
        note = getStringOrNull("note"),
        createdAtEpochMs = getLong(getColumnIndexOrThrow("createdAtEpochMs"))
    )
}

private fun Cursor.toSettings(): SettingsEntity {
    return SettingsEntity(
        id = 1,
        koperasiName = getString(getColumnIndexOrThrow("koperasiName")),
        koperasiAddress = getString(getColumnIndexOrThrow("koperasiAddress")),
        taxPercent = getDouble(getColumnIndexOrThrow("taxPercent")),
        discountPercent = getDouble(getColumnIndexOrThrow("discountPercent")),
        shuParameter = getDouble(getColumnIndexOrThrow("shuParameter")),
        updatedAtEpochMs = getLong(getColumnIndexOrThrow("updatedAtEpochMs"))
    )
}

private fun Cursor.toAudit(): AuditLogEntity {
    return AuditLogEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        userId = getLongOrNull("userId"),
        action = getString(getColumnIndexOrThrow("action")),
        entity = getString(getColumnIndexOrThrow("entity")),
        entityId = getLongOrNull("entityId"),
        detail = getStringOrNull("detail"),
        createdAtEpochMs = getLong(getColumnIndexOrThrow("createdAtEpochMs"))
    )
}

private fun Cursor.toPromo(): PromoEntity {
    return PromoEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        code = getString(getColumnIndexOrThrow("code")),
        name = getString(getColumnIndexOrThrow("name")),
        description = getStringOrNull("description"),
        discountPercent = getDouble(getColumnIndexOrThrow("discountPercent")),
        validUntilEpochMs = getLong(getColumnIndexOrThrow("validUntilEpochMs")),
        isActive = getInt(getColumnIndexOrThrow("isActive")) == 1
    )
}

private fun Cursor.toSale(): SaleEntity {
    return SaleEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        transactionId = getString(getColumnIndexOrThrow("transactionId")),
        cashierId = getLongOrNull("cashierId"),
        subtotal = getLong(getColumnIndexOrThrow("subtotal")),
        discount = getLong(getColumnIndexOrThrow("discount")),
        tax = getLong(getColumnIndexOrThrow("tax")),
        total = getLong(getColumnIndexOrThrow("total")),
        paymentMethod = getString(getColumnIndexOrThrow("paymentMethod")),
        status = getString(getColumnIndexOrThrow("status")),
        createdAtEpochMs = getLong(getColumnIndexOrThrow("createdAtEpochMs"))
    )
}

private fun Cursor.toSaleItem(): SaleItemEntity {
    return SaleItemEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        saleId = getLong(getColumnIndexOrThrow("saleId")),
        productId = getLongOrNull("productId"),
        productName = getString(getColumnIndexOrThrow("productName")),
        unitPrice = getLong(getColumnIndexOrThrow("unitPrice")),
        quantity = getLong(getColumnIndexOrThrow("quantity")),
        lineTotal = getLong(getColumnIndexOrThrow("lineTotal"))
    )
}

private fun Cursor.toSaleItemCount(): SaleItemCount {
    return SaleItemCount(
        saleId = getLong(getColumnIndexOrThrow("saleId")),
        itemCount = getLong(getColumnIndexOrThrow("itemCount"))
    )
}

private fun Cursor.toLatestSaleWithCashier(): LatestSaleWithCashier {
    return LatestSaleWithCashier(
        saleId = getLong(getColumnIndexOrThrow("saleId")),
        createdAtEpochMs = getLong(getColumnIndexOrThrow("createdAtEpochMs")),
        total = getLong(getColumnIndexOrThrow("total")),
        transactionId = getString(getColumnIndexOrThrow("transactionId")),
        cashierName = getString(getColumnIndexOrThrow("cashierName"))
    )
}

private fun Cursor.toSaleItemDetailRow(): SaleItemDetailRow {
    return SaleItemDetailRow(
        createdAtEpochMs = getLong(getColumnIndexOrThrow("createdAtEpochMs")),
        productName = getString(getColumnIndexOrThrow("productName")),
        category = getString(getColumnIndexOrThrow("category")),
        quantity = getLong(getColumnIndexOrThrow("quantity")),
        lineTotal = getLong(getColumnIndexOrThrow("lineTotal"))
    )
}

private inline fun <T> Cursor.toList(mapper: (Cursor) -> T): List<T> {
    val list = ArrayList<T>(count.coerceAtLeast(0))
    while (moveToNext()) list.add(mapper(this))
    return list
}

private fun Cursor.getStringOrNull(column: String): String? {
    val idx = getColumnIndex(column)
    if (idx < 0 || isNull(idx)) return null
    return getString(idx)
}

private fun Cursor.getLongOrNull(column: String): Long? {
    val idx = getColumnIndex(column)
    if (idx < 0 || isNull(idx)) return null
    return getLong(idx)
}
