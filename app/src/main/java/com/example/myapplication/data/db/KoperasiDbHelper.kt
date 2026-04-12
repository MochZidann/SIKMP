package com.example.myapplication.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class KoperasiDbHelper(context: Context) : SQLiteOpenHelper(context, "koperasi_merah_putih.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        ensureSchema(db, ifNotExists = false)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        ensureSchema(db, ifNotExists = true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Add description to promos if missing
            try { db.execSQL("ALTER TABLE promos ADD COLUMN description TEXT") } catch (e: Exception) {}
            // Add isActive to members if missing
            try { db.execSQL("ALTER TABLE members ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1") } catch (e: Exception) {}
        }
        if (oldVersion < 3) {
            // Add koperasiName and koperasiAddress to settings
            try { db.execSQL("ALTER TABLE settings ADD COLUMN koperasiName TEXT NOT NULL DEFAULT ''") } catch (e: Exception) {}
            try { db.execSQL("ALTER TABLE settings ADD COLUMN koperasiAddress TEXT NOT NULL DEFAULT ''") } catch (e: Exception) {}
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
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                price INTEGER NOT NULL,
                stock INTEGER NOT NULL,
                createdAtEpochMs INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("$index idx_products_name ON products(name)")

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
                cashierId INTEGER,
                subtotal INTEGER NOT NULL,
                discount INTEGER NOT NULL,
                tax INTEGER NOT NULL,
                total INTEGER NOT NULL,
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
                name TEXT NOT NULL,
                description TEXT,
                discountPercent REAL NOT NULL,
                validUntilEpochMs INTEGER NOT NULL,
                isActive INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
