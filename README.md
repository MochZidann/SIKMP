# 📱 SIKMP - Sistem Informasi Manajemen Koperasi Desa Merah Putih (KOPDESKEL)

Sistem Informasi Manajemen Koperasi (SIMK) Merah Putih adalah aplikasi mobile berbasis Android yang dikembangkan untuk mendigitalisasi seluruh proses operasional koperasi di Desa Jajar, Kecamatan Wates, Kabupaten Kediri. Aplikasi ini mengusung arsitektur *offline-first* dengan basis data SQLite untuk penyimpanan lokal, sehingga dapat berfungsi secara penuh tanpa memerlukan koneksi internet.

---

## 🔗 Tautan Repositori Proyek

* **Android App (Mobile Client):** [SIKMP Android](https://github.com/MochZidann/SIKMP.git)
* **Web & Backend API (Server):** [API Koperasi](https://github.com/MochZidann/api-koperasi.git)
* **Web Version:** [Kopdes-Jajar](https://kopdes-jajar.up.railway.app)

---

## 👥 Anggota Kelompok 3

| No | Nama Lengkap | NIM |
|:---|:---|:---|
| 1 | Anisya Puspita Dewi | 243107030044 |
| 2 | Fahmi Fikanjati | 243107030122 |
| 3 | Moch. Zidan Akbar Maulana | 243107030054 |
| 4 | Oktavea Alea Putri | 243107030118 |
| 5 | Sindi Dwi Lestari | 243107030099 |

---

## ✨ Fitur Lengkap Sistem

### 🛠️ Modul Admin Sistem
* **Manajemen Akun:** Tambah, edit, hapus, *import/export* data, dan *reset password*.
* **Data Anggota:** Pengelolaan basis data anggota koperasi.
* **Konfigurasi Promo:** Aktivasi/nonaktivasi diskon dengan validasi batas maksimal 100%.
* **Profil Koperasi:** Ubah nama, alamat, titik koordinat, dan unggah gambar QRIS.
* **Audit Trail:** Pemantauan log aktivitas dengan pencarian dan filter waktu.
* **Database Management:** *Backup* dan *restore* file `.db` SQLite secara lokal.

### 🛒 Modul Kasir (Point of Sale)
* **Transaksi:** Pemrosesan penjualan langsung via pencarian teks atau pemindai *barcode*.
* **Keranjang Belanja:** Kalkulasi dinamis untuk promo dan kembalian.
* **Metode Pembayaran:** Tunai, Transfer, dan QRIS statis.
* **Cetak Struk:** Integrasi printer thermal Bluetooth (RFCOMM/SPP) dan ekspor PDF.
* **Laporan Harian:** Grafik dan statistik riwayat penjualan kasir mandiri.

### 📦 Modul Admin Gudang (Manajemen Inventori)
* **Katalog Produk:** Penetapan harga, batas stok minimum, masa kedaluwarsa, dan gambar.
* **Kategori Barang:** Klasifikasi produk (Sembako, Pupuk, dll).
* **Mutasi Stok:** Pencatatan barang masuk/keluar dengan validasi cegah stok negatif.
* **Restock Alert:** Notifikasi *dashboard* untuk barang di bawah batas minimum.
* **Laporan Mutasi:** Jejak perubahan inventori dengan fitur ekspor Excel/PDF.

### 📊 Modul Owner / Pengawas (Executive Dashboard)
* **Executive Dashboard:** Grafik tren pendapatan 7 hari dan daftar 5 produk terlaris.
* **Laporan Terpusat:** Data penjualan seluruh unit dengan filter waktu kustom.
* **Laporan Stok:** Komparasi barang masuk, barang keluar, dan selisih bersih.
* **Kesehatan Inventori:** Kalkulasi nilai aset stok, *dead stock*, dan total *SKU* aktif.

---

## 💻 Lingkungan & Tech Stack

* **Bahasa Pemrograman:** Kotlin (Target SDK 14 / API 34).
* **Antarmuka Pengguna:** Material Design 3, ViewPager2, TabLayout, dan RecyclerView.
* **Basis Data:** SQLite (Sistem *Offline-First*).
* **Hardware Interface:** Bluetooth API untuk Printer Thermal.
* **Library Export:** Apache POI / iText (Excel dan PDF).

---

## 🚀 Panduan Memulai (Quick Start)

### 1️⃣ Konfigurasi Backend (Laravel API)

**Prasyarat:**
* PHP & Composer terinstal di sistem.
* MySQL Server aktif.

**Langkah Instalasi:**
1.  *Clone* repositori backend:
    ```bash
    git clone [https://github.com/MochZidann/api-koperasi.git](https://github.com/MochZidann/api-koperasi.git)
    ```
2.  Masuk ke direktori dan instal dependensi:
    ```bash
    cd api-koperasi
    composer install
    ```
3.  Salin konfigurasi *environment*:
    ```bash
    cp .env.example .env
    ```
4.  Buka file `.env` dan sesuaikan kredensial basis data (`DB_DATABASE`, `DB_USERNAME`, `DB_PASSWORD`).
5.  *Generate app key* dan jalankan migrasi:
    ```bash
    php artisan key:generate
    php artisan migrate
    ```
6.  Jalankan server lokal:
    ```bash
    php artisan serve --host=0.0.0.0 --port=8000
    ```
    *(Catatan: `--host=0.0.0.0` digunakan agar API dapat diakses oleh perangkat Android di jaringan WiFi yang sama).*

### 2️⃣ Konfigurasi Frontend (Android Client)

**Prasyarat:**
* Android Studio versi terbaru.
* SDK Android 14 (API 34).
* Perangkat fisik atau emulator (Minimal Android 10 / API 29, RAM 2GB).

**Langkah Instalasi:**
1.  *Clone* repositori Android:
    ```bash
    git clone [https://github.com/MochZidann/SIKMP.git](https://github.com/MochZidann/SIKMP.git)
    ```
2.  Buka Android Studio, pilih **Open**, dan arahkan ke folder `SIKMP`.
3.  Tunggu proses **Gradle Sync** selesai.
4.  **Konfigurasi URL API:**
    * Buka file konfigurasi jaringan (kelas `ApiConfig` atau file *helper*).
    * Ubah nilai `BASE_URL` menggunakan alamat IP lokal komputer server.
    ```kotlin
    const val BASE_URL = "[http://192.168.](http://192.168.)x.x:8000/api/"
    ```
5.  Sambungkan perangkat Android dan klik tombol **Run 'app'**.

---
