package com.example.myapplication.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class KoperasiDbHelper(context: Context) : SQLiteOpenHelper(context, "koperasi_merah_putih.db", null, 11) {
    override fun onCreate(db: SQLiteDatabase) {
        ensureSchema(db, ifNotExists = false)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        ensureSchema(db, ifNotExists = true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try { db.execSQL("ALTER TABLE promos ADD COLUMN description TEXT") } catch (e: Exception) {}
            try { db.execSQL("ALTER TABLE members ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1") } catch (e: Exception) {}
        }
        if (oldVersion < 3) {
            try { db.execSQL("ALTER TABLE settings ADD COLUMN koperasiName TEXT NOT NULL DEFAULT ''") } catch (e: Exception) {}
            try { db.execSQL("ALTER TABLE settings ADD COLUMN koperasiAddress TEXT NOT NULL DEFAULT ''") } catch (e: Exception) {}
        }
        if (oldVersion < 4) {
            try {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS categories(
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        createdAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_categories_name ON categories(name)")
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO categories(name, createdAtEpochMs)
                    SELECT DISTINCT category, strftime('%s','now')*1000
                    FROM products
                    WHERE category IS NOT NULL AND TRIM(category) <> ''
                    """.trimIndent()
                )
            } catch (_: Exception) {}
        }
        if (oldVersion < 5) {
            try { db.execSQL("ALTER TABLE settings ADD COLUMN koperasiPhone TEXT NOT NULL DEFAULT ''") } catch (e: Exception) {}
        }
        if (oldVersion < 6) {
            ensureColumn(db, "products", "barcode", "TEXT")
        }
        if (oldVersion < 7) {
            ensureColumn(db, "users", "needsPasswordReset", "INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 8) {
            ensureColumn(db, "promos", "code", "TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 9) {
            ensureColumn(db, "products", "imagePath", "TEXT")
        }
        if (oldVersion < 10) {
            ensureColumn(db, "products", "minimumStock", "INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 11) {
            ensureColumn(db, "products", "expiredDateEpochMs", "INTEGER")
        }
    }

    private fun ensureSchema(db: SQLiteDatabase, ifNotExists: Boolean) {
        val table = if (ifNotExists) "CREATE TABLE IF NOT EXISTS" else "CREATE TABLE"
        val index = if (ifNotExists) "CREATE INDEX IF NOT EXISTS" else "CREATE INDEX"

        db.execSQL(
            """
            $table users(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                username TEXT NOT NULL UNIQUE,
                passwordHash TEXT NOT NULL,
                salt TEXT NOT NULL,
                role TEXT NOT NULL,
                isActive INTEGER NOT NULL,
                needsPasswordReset INTEGER NOT NULL DEFAULT 0,
                createdAtEpochMs INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("$index idx_users_role ON users(role)")

        db.execSQL(
            """
            $table members(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                memberNo TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                phone TEXT,
                address TEXT,
                isActive INTEGER NOT NULL DEFAULT 1,
                createdAtEpochMs INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            $table products(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                barcode TEXT,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                price INTEGER NOT NULL,
                stock INTEGER NOT NULL,
                minimumStock INTEGER NOT NULL DEFAULT 0,
                expiredDateEpochMs INTEGER,
                imagePath TEXT,
                createdAtEpochMs INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("$index idx_products_name ON products(name)")

        db.execSQL(
            """
            $table categories(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                createdAtEpochMs INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("$index idx_categories_name ON categories(name)")

        db.execSQL(
            """
            $table stock_movements(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                productId INTEGER NOT NULL,
                userId INTEGER,
                type TEXT NOT NULL,
                quantityDelta INTEGER NOT NULL,
                note TEXT,
                createdAtEpochMs INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("$index idx_stock_movements_created ON stock_movements(createdAtEpochMs)")

        db.execSQL(
            """
            $table sales(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                transactionId TEXT NOT NULL UNIQUE,
                cashierId INTEGER,
                subtotal INTEGER NOT NULL,
                discount INTEGER NOT NULL,
                tax INTEGER NOT NULL,
                total INTEGER NOT NULL,
                paymentMethod TEXT NOT NULL DEFAULT 'TUNAI',
                status TEXT NOT NULL DEFAULT 'SUCCESS',
                createdAtEpochMs INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("$index idx_sales_created ON sales(createdAtEpochMs)")

        db.execSQL(
            """
            $table sale_items(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                saleId INTEGER NOT NULL,
                productId INTEGER,
                productName TEXT NOT NULL,
                unitPrice INTEGER NOT NULL,
                quantity INTEGER NOT NULL,
                lineTotal INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("$index idx_sale_items_saleId ON sale_items(saleId)")

        db.execSQL(
            """
            $table settings(
                id INTEGER PRIMARY KEY,
                koperasiName TEXT NOT NULL DEFAULT '',
                koperasiAddress TEXT NOT NULL DEFAULT '',
                koperasiPhone TEXT NOT NULL DEFAULT '',
                taxPercent REAL NOT NULL,
                discountPercent REAL NOT NULL,
                shuParameter REAL NOT NULL,
                updatedAtEpochMs INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            $table audit_logs(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                userId INTEGER,
                action TEXT NOT NULL,
                entity TEXT NOT NULL,
                entityId INTEGER,
                detail TEXT,
                createdAtEpochMs INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("$index idx_audit_logs_created ON audit_logs(createdAtEpochMs)")

        db.execSQL(
            """
            $table promos(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                code TEXT NOT NULL DEFAULT '',
                name TEXT NOT NULL,
                description TEXT,
                discountPercent REAL NOT NULL,
                validUntilEpochMs INTEGER NOT NULL,
                isActive INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("$index idx_promos_code ON promos(code)")

        ensureColumn(db, table = "products", column = "barcode", definition = "TEXT")
        ensureColumn(db, table = "products", column = "imagePath", definition = "TEXT")
        ensureColumn(db, table = "members", column = "isActive", definition = "INTEGER NOT NULL DEFAULT 1")
        ensureColumn(db, table = "promos", column = "description", definition = "TEXT")
        ensureColumn(db, table = "promos", column = "code", definition = "TEXT NOT NULL DEFAULT ''")
        ensureColumn(db, table = "settings", column = "koperasiPhone", definition = "TEXT NOT NULL DEFAULT ''")
        ensureColumn(db, table = "users", column = "needsPasswordReset", definition = "INTEGER NOT NULL DEFAULT 0")
        ensureColumn(db, table = "sales", column = "transactionId", definition = "TEXT NOT NULL DEFAULT ''")
        ensureColumn(db, table = "sales", column = "paymentMethod", definition = "TEXT NOT NULL DEFAULT 'TUNAI'")
        ensureColumn(db, table = "sales", column = "status", definition = "TEXT NOT NULL DEFAULT 'SUCCESS'")
        ensureColumn(db, table = "products", column = "minimumStock", definition = "INTEGER NOT NULL DEFAULT 0")
        ensureColumn(db, table = "products", column = "expiredDateEpochMs", definition = "INTEGER")
    }

    private fun ensureColumn(db: SQLiteDatabase, table: String, column: String, definition: String) {
        val exists = db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val nameIdx = c.getColumnIndex("name")
            if (nameIdx < 0) return@use false
            var found = false
            while (c.moveToNext()) {
                if (c.getString(nameIdx) == column) {
                    found = true
                    break
                }
            }
            found
        }
        if (exists) return
        db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
    }
}
