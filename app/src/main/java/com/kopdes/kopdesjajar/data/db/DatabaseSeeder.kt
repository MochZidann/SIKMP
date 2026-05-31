package com.kopdes.kopdesjajar.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.kopdes.kopdesjajar.data.firebase.FirestoreManager
import com.kopdes.kopdesjajar.data.model.Role
import com.kopdes.kopdesjajar.data.security.PasswordHasher

object DatabaseSeeder {
    suspend fun ensureSeeded(context: Context) {
        val db = AppDatabase.get(context)
        val userDao = db.userDao()
        val settingsDao = db.settingsDao()
        val firestore = FirestoreManager()

        if (settingsDao.get() == null) {
            val defaultSettings = SettingsEntity()
            settingsDao.upsert(defaultSettings)
            firestore.syncSettings(defaultSettings)
        }

        val defaults = listOf(
            Triple("Admin Sistem", "admin", Role.ADMIN_SISTEM),
            Triple("Kasir", "kasir", Role.KASIR),
            Triple("Admin Gudang", "gudang", Role.ADMIN_GUDANG),
            Triple("Owner", "owner", Role.OWNER_PENGAWAS)
        )

        for ((name, username, role) in defaults) {
            if (userDao.findByUsername(username) == null) {
                val salt = PasswordHasher.generateSalt()
                val hash = PasswordHasher.hash("123456", salt)
                val newUser = UserEntity(
                    name = name,
                    username = username,
                    passwordHash = hash,
                    salt = salt,
                    role = role
                )
                val id = userDao.insert(newUser)
                // PENTING: Push ke Firebase supaya HP lain bisa login pake user ini
                firestore.syncUser(newUser.copy(id = id))
                Log.d("SyncDebug", "🚀 Seeded & Pushed user: $username to Firebase")
            }
        }
    }

    /**
     * Insert dummy data produk, anggota, dan penjualan.
     * AMAN dipanggil berkali-kali — hanya insert jika products & members masih kosong.
     */
    fun seedDummyData(context: Context) {
        val helper = KoperasiDbHelper(context)
        val db = helper.writableDatabase

        val productCount = db.rawQuery("SELECT COUNT(*) FROM products", null).use { c ->
            c.moveToFirst(); c.getInt(0)
        }
        val memberCount = db.rawQuery("SELECT COUNT(*) FROM members", null).use { c ->
            c.moveToFirst(); c.getInt(0)
        }

        if (productCount > 0 && memberCount > 0) {
            Log.d("Seeder", "ℹ️ Dummy data sudah ada, skip seeding.")
            db.close()
            return
        }

        Log.d("Seeder", "🌱 Mulai insert dummy data...")
        db.beginTransaction()
        try {
            seedCategories(db)
            seedProducts(db)
            seedMembers(db)
            seedSales(db)
            seedSaleItems(db)
            seedStockMovements(db)
            db.setTransactionSuccessful()
            Log.d("Seeder", "✅ Dummy data berhasil diinsert!")
        } catch (e: Exception) {
            Log.e("Seeder", "❌ Gagal insert dummy data: ${e.message}", e)
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    private fun exec(db: SQLiteDatabase, sql: String) = db.execSQL(sql)

    private fun seedCategories(db: SQLiteDatabase) {
        listOf(
            "(1,'Sembako',1746057600000,0)",
            "(2,'Minuman',1746057600000,0)",
            "(3,'Snack & Camilan',1746057600000,0)",
            "(4,'Perawatan Diri',1746057600000,0)",
            "(5,'Alat Tulis',1746057600000,0)",
            "(6,'Kebutuhan Rumah',1746057600000,0)"
        ).forEach { exec(db, "INSERT OR IGNORE INTO categories(id,name,createdAtEpochMs,isSynced) VALUES $it") }
        Log.d("Seeder", "  ✓ 6 kategori")
    }

    private fun seedProducts(db: SQLiteDatabase) {
        listOf(
            // SEMBAKO
            "(1,'8991234000011','Beras Premium 5kg','Sembako',72000,65000,80,10,NULL,NULL,0,1746057600000)",
            "(2,'8991234000012','Gula Pasir 1kg','Sembako',16000,13500,120,20,NULL,NULL,0,1746057600000)",
            "(3,'8991234000013','Minyak Goreng 1L','Sembako',18500,16000,100,20,NULL,NULL,0,1746057600000)",
            "(4,'8991234000014','Tepung Terigu 1kg','Sembako',12000,9500,60,10,NULL,NULL,0,1746057600000)",
            "(5,'8991234000015','Mie Instan Goreng (1 dus)','Sembako',115000,95000,30,5,NULL,NULL,0,1746057600000)",
            "(6,'8991234000016','Garam Dapur 500g','Sembako',5000,3500,90,15,NULL,NULL,0,1746057600000)",
            "(7,'8991234000017','Kecap Manis 135ml','Sembako',7500,5800,75,10,1780272000000,NULL,0,1746057600000)",
            // MINUMAN
            "(8,'8991234000021','Air Mineral 600ml','Minuman',4000,2800,200,30,1780272000000,NULL,0,1746057600000)",
            "(9,'8991234000022','Teh Manis Kotak 200ml','Minuman',4500,3200,150,24,1775088000000,NULL,0,1746057600000)",
            "(10,'8991234000023','Susu UHT Cokelat 250ml','Minuman',8000,6200,96,12,1775088000000,NULL,0,1746057600000)",
            "(11,'8991234000024','Kopi Sachet (1 dus 10pcs)','Minuman',25000,20000,50,10,NULL,NULL,0,1746057600000)",
            "(12,'8991234000025','Minuman Energi 250ml','Minuman',8500,6500,72,12,1775088000000,NULL,0,1746057600000)",
            // SNACK
            "(13,'8991234000031','Kerupuk Udang 250g','Snack & Camilan',12000,9000,60,10,1775088000000,NULL,0,1746057600000)",
            "(14,'8991234000032','Biskuit Sandwich 100g','Snack & Camilan',9000,7000,80,10,1775088000000,NULL,0,1746057600000)",
            "(15,'8991234000033','Wafer Cokelat 145g','Snack & Camilan',11000,8500,75,10,1775088000000,NULL,0,1746057600000)",
            "(16,'8991234000034','Permen Mint Isi 12','Snack & Camilan',4000,2800,100,20,NULL,NULL,0,1746057600000)",
            "(17,'8991234000035','Kacang Atom 100g','Snack & Camilan',8000,6000,90,15,1775088000000,NULL,0,1746057600000)",
            // PERAWATAN DIRI
            "(18,'8991234000041','Sabun Mandi Batang 100g','Perawatan Diri',5000,3700,100,20,NULL,NULL,0,1746057600000)",
            "(19,'8991234000042','Shampo Sachet 10ml','Perawatan Diri',2000,1400,200,50,NULL,NULL,0,1746057600000)",
            "(20,'8991234000043','Pasta Gigi 75g','Perawatan Diri',11000,8500,80,15,NULL,NULL,0,1746057600000)",
            "(21,'8991234000044','Deterjen Bubuk 500g','Perawatan Diri',13000,10500,70,10,NULL,NULL,0,1746057600000)",
            "(22,'8991234000045','Sabun Cuci Piring 200ml','Perawatan Diri',8500,6500,60,10,NULL,NULL,0,1746057600000)",
            // ALAT TULIS
            "(23,'8991234000051','Pulpen Biru (1 lusin)','Alat Tulis',15000,11000,40,5,NULL,NULL,0,1746057600000)",
            "(24,'8991234000052','Buku Tulis 40 lembar','Alat Tulis',5000,3500,100,20,NULL,NULL,0,1746057600000)",
            "(25,'8991234000053','Penggaris 30cm','Alat Tulis',5500,4000,50,10,NULL,NULL,0,1746057600000)",
            "(26,'8991234000054','Pensil 2B','Alat Tulis',3000,2000,80,20,NULL,NULL,0,1746057600000)",
            // KEBUTUHAN RUMAH
            "(27,'8991234000061','Kantong Plastik 1kg (isi 50)','Kebutuhan Rumah',8000,6000,60,10,NULL,NULL,0,1746057600000)",
            "(28,'8991234000062','Korek Api Kayu (1 pak)','Kebutuhan Rumah',4000,2800,120,20,NULL,NULL,0,1746057600000)",
            "(29,'8991234000063','Sabun Colek 200g','Kebutuhan Rumah',6000,4500,80,15,NULL,NULL,0,1746057600000)",
            "(30,'8991234000064','Tisu Toilet 4 Roll','Kebutuhan Rumah',18000,14000,50,10,NULL,NULL,0,1746057600000)"
        ).forEach { exec(db, "INSERT OR IGNORE INTO products(id,barcode,name,category,price,purchasePrice,stock,minimumStock,expiredDateEpochMs,imagePath,isSynced,createdAtEpochMs) VALUES $it") }
        Log.d("Seeder", "  ✓ 30 produk")
    }

    private fun seedMembers(db: SQLiteDatabase) {
        listOf(
            "(1,'KMP-001','Siti Aminah','08123456001','Jl. Merdeka No.12, RT 01/02',1,0,1714521600000)",
            "(2,'KMP-002','Budi Santoso','08123456002','Jl. Pahlawan No.5, RT 03/04',1,0,1714521600000)",
            "(3,'KMP-003','Dewi Rahayu','08123456003','Jl. Kenanga No.8, RT 02/01',1,0,1717200000000)",
            "(4,'KMP-004','Andi Firmansyah','08123456004','Jl. Melati No.23, RT 05/03',1,0,1717200000000)",
            "(5,'KMP-005','Ratna Wulandari','08123456005','Jl. Mawar No.17, RT 01/04',1,0,1719792000000)",
            "(6,'KMP-006','Hendra Kusuma','08123456006','Jl. Anggrek No.9, RT 04/02',1,0,1719792000000)",
            "(7,'KMP-007','Sri Wahyuni','08123456007','Jl. Dahlia No.31, RT 03/01',1,0,1722384000000)",
            "(8,'KMP-008','Agus Priyanto','08123456008','Jl. Flamboyan No.6, RT 02/03',1,0,1722384000000)",
            "(9,'KMP-009','Fitria Handayani','08123456009','Jl. Cempaka No.14, RT 01/01',1,0,1724976000000)",
            "(10,'KMP-010','Rizky Nugroho','08123456010','Jl. Teratai No.2, RT 05/04',1,0,1724976000000)"
        ).forEach { exec(db, "INSERT OR IGNORE INTO members(id,memberNo,name,phone,address,isActive,isSynced,createdAtEpochMs) VALUES $it") }
        Log.d("Seeder", "  ✓ 10 anggota")
    }

    private fun seedSales(db: SQLiteDatabase) {
        listOf(
            "(1,'TRX-20260531-001',2,87000,0,0,87000,'TUNAI','SUCCESS',0,1748649600000)",
            "(2,'TRX-20260531-002',2,45500,0,0,45500,'QRIS','SUCCESS',0,1748653200000)",
            "(3,'TRX-20260531-003',3,132000,5000,0,127000,'TUNAI','SUCCESS',0,1748660400000)",
            "(4,'TRX-20260530-001',2,56000,0,0,56000,'TUNAI','SUCCESS',0,1748563200000)",
            "(5,'TRX-20260529-001',2,28000,0,0,28000,'QRIS','SUCCESS',0,1748476800000)",
            "(6,'TRX-20260528-001',3,95500,0,0,95500,'TUNAI','SUCCESS',0,1748390400000)",
            "(7,'TRX-20260527-001',2,47000,2000,0,45000,'TUNAI','SUCCESS',0,1748304000000)",
            "(8,'TRX-20260526-001',2,183000,0,0,183000,'TRANSFER','SUCCESS',0,1748217600000)",
            "(9,'TRX-20260524-001',2,64000,0,0,64000,'TUNAI','SUCCESS',0,1748044800000)",
            "(10,'TRX-20260523-001',3,38000,0,0,38000,'QRIS','SUCCESS',0,1747958400000)",
            "(11,'TRX-20260522-001',2,112000,0,0,112000,'TUNAI','SUCCESS',0,1747872000000)",
            "(12,'TRX-20260521-001',2,29500,0,0,29500,'TUNAI','SUCCESS',0,1747785600000)",
            "(13,'TRX-20260520-001',3,77000,3000,0,74000,'TUNAI','SUCCESS',0,1747699200000)",
            "(14,'TRX-20260517-001',2,52000,0,0,52000,'TUNAI','SUCCESS',0,1747440000000)",
            "(15,'TRX-20260516-001',2,91500,0,0,91500,'QRIS','SUCCESS',0,1747353600000)",
            "(16,'TRX-20260515-001',3,43000,0,0,43000,'TUNAI','SUCCESS',0,1747267200000)",
            "(17,'TRX-20260514-001',2,168000,8000,0,160000,'TRANSFER','SUCCESS',0,1747180800000)",
            "(18,'TRX-20260510-001',2,35000,0,0,35000,'TUNAI','SUCCESS',0,1746835200000)",
            "(19,'TRX-20260508-001',3,126000,0,0,126000,'TUNAI','SUCCESS',0,1746662400000)",
            "(20,'TRX-20260505-001',2,59000,0,0,59000,'QRIS','SUCCESS',0,1746403200000)"
        ).forEach { exec(db, "INSERT OR IGNORE INTO sales(id,transactionId,cashierId,subtotal,discount,tax,total,paymentMethod,status,isSynced,createdAtEpochMs) VALUES $it") }
        Log.d("Seeder", "  ✓ 20 transaksi")
    }

    private fun seedSaleItems(db: SQLiteDatabase) {
        listOf(
            "(1,1,1,'Beras Premium 5kg',72000,1,72000)",
            "(2,1,2,'Gula Pasir 1kg',15000,1,15000)",
            "(3,2,8,'Air Mineral 600ml',4000,3,12000)",
            "(4,2,14,'Biskuit Sandwich 100g',9000,2,18000)",
            "(5,2,19,'Shampo Sachet 10ml',2000,5,10000)",
            "(6,2,16,'Permen Mint Isi 12',4000,1,4000)",
            "(7,3,1,'Beras Premium 5kg',72000,1,72000)",
            "(8,3,3,'Minyak Goreng 1L',18500,2,37000)",
            "(9,3,6,'Garam Dapur 500g',5000,1,5000)",
            "(10,3,7,'Kecap Manis 135ml',7500,1,7500)",
            "(11,3,8,'Air Mineral 600ml',4000,2,8000)",
            "(12,3,29,'Sabun Colek 200g',6000,2,12000)",
            "(13,4,2,'Gula Pasir 1kg',16000,2,32000)",
            "(14,4,4,'Tepung Terigu 1kg',12000,2,24000)",
            "(15,5,13,'Kerupuk Udang 250g',12000,1,12000)",
            "(16,5,15,'Wafer Cokelat 145g',11000,1,11000)",
            "(17,5,16,'Permen Mint Isi 12',4000,1,5000)",
            "(18,6,18,'Sabun Mandi Batang 100g',5000,3,15000)",
            "(19,6,20,'Pasta Gigi 75g',11000,2,22000)",
            "(20,6,21,'Deterjen Bubuk 500g',13000,2,26000)",
            "(21,6,22,'Sabun Cuci Piring 200ml',8500,1,8500)",
            "(22,6,30,'Tisu Toilet 4 Roll',18000,1,18000)",
            "(23,7,9,'Teh Manis Kotak 200ml',4500,4,18000)",
            "(24,7,10,'Susu UHT Cokelat 250ml',8000,2,16000)",
            "(25,7,24,'Buku Tulis 40 lembar',5000,2,10000)",
            "(26,7,26,'Pensil 2B',3000,1,3000)",
            "(27,8,1,'Beras Premium 5kg',72000,2,144000)",
            "(28,8,2,'Gula Pasir 1kg',16000,2,32000)",
            "(29,8,3,'Minyak Goreng 1L',18500,1,18500)",
            "(30,9,12,'Minuman Energi 250ml',8500,2,17000)",
            "(31,9,13,'Kerupuk Udang 250g',12000,2,24000)",
            "(32,9,17,'Kacang Atom 100g',8000,2,16000)",
            "(33,9,16,'Permen Mint Isi 12',4000,2,8000)",
            "(34,10,23,'Pulpen Biru (1 lusin)',15000,1,15000)",
            "(35,10,24,'Buku Tulis 40 lembar',5000,3,15000)",
            "(36,10,25,'Penggaris 30cm',5500,1,5500)",
            "(37,10,26,'Pensil 2B',3000,1,3000)",
            "(38,11,1,'Beras Premium 5kg',72000,1,72000)",
            "(39,11,3,'Minyak Goreng 1L',18500,1,18500)",
            "(40,11,6,'Garam Dapur 500g',5000,1,5000)",
            "(41,11,7,'Kecap Manis 135ml',7500,1,7500)",
            "(42,12,19,'Shampo Sachet 10ml',2000,5,10000)",
            "(43,12,18,'Sabun Mandi Batang 100g',5000,2,10000)",
            "(44,12,16,'Permen Mint Isi 12',4000,1,4000)",
            "(45,12,8,'Air Mineral 600ml',4000,1,4000)",
            "(46,13,2,'Gula Pasir 1kg',16000,2,32000)",
            "(47,13,4,'Tepung Terigu 1kg',12000,2,24000)",
            "(48,13,14,'Biskuit Sandwich 100g',9000,2,18000)",
            "(49,14,6,'Garam Dapur 500g',5000,2,10000)",
            "(50,14,7,'Kecap Manis 135ml',7500,2,15000)",
            "(51,14,28,'Korek Api Kayu (1 pak)',4000,2,8000)",
            "(52,14,29,'Sabun Colek 200g',6000,2,12000)",
            "(53,15,8,'Air Mineral 600ml',4000,10,40000)",
            "(54,15,9,'Teh Manis Kotak 200ml',4500,6,27000)",
            "(55,15,11,'Kopi Sachet (1 dus 10pcs)',25000,1,25000)",
            "(56,16,8,'Air Mineral 600ml',4000,2,8000)",
            "(57,16,13,'Kerupuk Udang 250g',12000,1,12000)",
            "(58,16,19,'Shampo Sachet 10ml',2000,5,10000)",
            "(59,16,26,'Pensil 2B',3000,1,3000)",
            "(60,17,1,'Beras Premium 5kg',72000,2,144000)",
            "(61,17,3,'Minyak Goreng 1L',18500,1,18500)",
            "(62,17,21,'Deterjen Bubuk 500g',13000,1,13000)",
            "(63,18,14,'Biskuit Sandwich 100g',9000,1,9000)",
            "(64,18,16,'Permen Mint Isi 12',4000,2,8000)",
            "(65,18,8,'Air Mineral 600ml',4000,2,8000)",
            "(66,18,27,'Kantong Plastik 1kg (isi 50)',8000,1,8000)",
            "(67,19,1,'Beras Premium 5kg',72000,1,72000)",
            "(68,19,2,'Gula Pasir 1kg',16000,2,32000)",
            "(69,19,30,'Tisu Toilet 4 Roll',18000,1,18000)",
            "(70,20,10,'Susu UHT Cokelat 250ml',8000,3,24000)",
            "(71,20,15,'Wafer Cokelat 145g',11000,2,22000)",
            "(72,20,17,'Kacang Atom 100g',8000,1,8000)",
            "(73,20,16,'Permen Mint Isi 12',4000,1,5000)"
        ).forEach { exec(db, "INSERT OR IGNORE INTO sale_items(id,saleId,productId,productName,unitPrice,quantity,lineTotal) VALUES $it") }
        Log.d("Seeder", "  ✓ 73 sale items")
    }

    private fun seedStockMovements(db: SQLiteDatabase) {
        listOf(
            "(1,1,1,'IN',100,'Stok awal masuk dari supplier',0,1746057600000)",
            "(2,2,1,'IN',150,'Stok awal masuk dari supplier',0,1746057600000)",
            "(3,3,1,'IN',120,'Stok awal masuk dari supplier',0,1746057600000)",
            "(4,5,1,'IN',40,'Stok awal masuk dari supplier',0,1746057600000)",
            "(5,8,1,'IN',250,'Stok awal air mineral',0,1746057600000)",
            "(6,18,1,'IN',120,'Stok awal sabun mandi',0,1746057600000)",
            "(7,1,1,'IN',30,'Restock beras dari UD Sumber Makmur',0,1747440000000)",
            "(8,2,1,'IN',50,'Restock gula dari distributor',0,1747440000000)",
            "(9,3,1,'IN',40,'Restock minyak goreng',0,1747440000000)",
            "(10,8,1,'IN',100,'Restock air mineral 600ml',0,1747958400000)",
            "(11,9,1,'IN',60,'Restock teh kotak',0,1747958400000)",
            "(12,1,2,'SALE',-5,'Terjual TRX-20260531-001',0,1748649600000)",
            "(13,8,2,'SALE',-15,'Terjual berbagai transaksi Mei',0,1748649600000)",
            "(14,2,2,'SALE',-8,'Terjual berbagai transaksi Mei',0,1748649600000)",
            "(15,7,1,'OUT',-3,'Kecap expired, dimusnahkan',0,1747699200000)",
            "(16,9,1,'OUT',-6,'Teh kotak dipindah gudang',0,1748217600000)"
        ).forEach { exec(db, "INSERT OR IGNORE INTO stock_movements(id,productId,userId,type,quantityDelta,note,isSynced,createdAtEpochMs) VALUES $it") }
        Log.d("Seeder", "  ✓ 16 stock movements")
    }
}
