package com.kopdes.kopdesjajar.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class KoperasiDbHelper(context: Context) : SQLiteOpenHelper(context, "koperasi_merah_putih.db", null, 16) {
    override fun onCreate(db: SQLiteDatabase) {
        ensureSchema(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        ensureSchema(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 13) {
            val tablesToSync = listOf("members", "products", "stock_movements", "sales", "settings", "audit_logs", "promos")
            tablesToSync.forEach { table ->
                ensureColumn(db, table, "isSynced", "INTEGER NOT NULL DEFAULT 0")
            }
            ensureColumn(db, "settings", "latitude", "REAL")
            ensureColumn(db, "settings", "longitude", "REAL")
        }
        if (oldVersion < 14) {
            db.execSQL("CREATE TABLE IF NOT EXISTS categories(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, createdAtEpochMs INTEGER NOT NULL, isSynced INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE IF NOT EXISTS sale_items(id INTEGER PRIMARY KEY AUTOINCREMENT, saleId INTEGER NOT NULL, productId INTEGER, productName TEXT NOT NULL, unitPrice INTEGER NOT NULL, quantity INTEGER NOT NULL, lineTotal INTEGER NOT NULL, FOREIGN KEY(saleId) REFERENCES sales(id) ON DELETE CASCADE)")
        }
        if (oldVersion < 15) {
            ensureColumn(db, "users", "isSynced", "INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 16) {
            ensureColumn(db, "promos", "promoType", "TEXT NOT NULL DEFAULT 'TRANSACTION'")
            ensureColumn(db, "promos", "minimumPurchase", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(db, "promos", "productId", "INTEGER")
        }
    }

    private fun ensureSchema(db: SQLiteDatabase) {
        val table = "CREATE TABLE IF NOT EXISTS"
        val index = "CREATE INDEX IF NOT EXISTS"

        db.execSQL("""
            $table users(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                username TEXT NOT NULL UNIQUE,
                passwordHash TEXT NOT NULL,
                salt TEXT NOT NULL,
                role TEXT NOT NULL,
                isActive INTEGER NOT NULL,
                needsPasswordReset INTEGER NOT NULL DEFAULT 0,
                isSynced INTEGER NOT NULL DEFAULT 0,
                createdAtEpochMs INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            $table members(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                memberNo TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                phone TEXT,
                address TEXT,
                isActive INTEGER NOT NULL DEFAULT 1,
                isSynced INTEGER NOT NULL DEFAULT 0,
                createdAtEpochMs INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
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
                purchasePrice INTEGER NOT NULL DEFAULT 0,
                isSynced INTEGER NOT NULL DEFAULT 0,
                createdAtEpochMs INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            $table categories(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                createdAtEpochMs INTEGER NOT NULL,
                isSynced INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            $table stock_movements(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                productId INTEGER NOT NULL,
                userId INTEGER,
                type TEXT NOT NULL,
                quantityDelta INTEGER NOT NULL,
                note TEXT,
                isSynced INTEGER NOT NULL DEFAULT 0,
                createdAtEpochMs INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
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
                isSynced INTEGER NOT NULL DEFAULT 0,
                createdAtEpochMs INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            $table sale_items(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                saleId INTEGER NOT NULL,
                productId INTEGER,
                productName TEXT NOT NULL,
                unitPrice INTEGER NOT NULL,
                quantity INTEGER NOT NULL,
                lineTotal INTEGER NOT NULL,
                FOREIGN KEY(saleId) REFERENCES sales(id) ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("""
            $table settings(
                id INTEGER PRIMARY KEY,
                koperasiName TEXT NOT NULL DEFAULT '',
                koperasiAddress TEXT NOT NULL DEFAULT '',
                koperasiPhone TEXT NOT NULL DEFAULT '',
                taxPercent REAL NOT NULL,
                discountPercent REAL NOT NULL,
                shuParameter REAL NOT NULL,
                latitude REAL,
                longitude REAL,
                isSynced INTEGER NOT NULL DEFAULT 0,
                updatedAtEpochMs INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            $table audit_logs(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                userId INTEGER,
                action TEXT NOT NULL,
                entity TEXT NOT NULL,
                entityId INTEGER,
                detail TEXT,
                isSynced INTEGER NOT NULL DEFAULT 0,
                createdAtEpochMs INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            $table promos(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                code TEXT NOT NULL DEFAULT '',
                name TEXT NOT NULL,
                description TEXT,
                discountPercent REAL NOT NULL,
                validUntilEpochMs INTEGER NOT NULL,
                promoType TEXT NOT NULL DEFAULT 'TRANSACTION',
                minimumPurchase INTEGER NOT NULL DEFAULT 0,
                productId INTEGER,
                isSynced INTEGER NOT NULL DEFAULT 0,
                isActive INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("$index idx_users_role ON users(role)")
        db.execSQL("$index idx_products_name ON products(name)")
        db.execSQL("$index idx_sales_created ON sales(createdAtEpochMs)")
        db.execSQL("$index idx_audit_logs_created ON audit_logs(createdAtEpochMs)")
        db.execSQL("$index idx_promos_code ON promos(code)")
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
