"""
Script Upload Dummy Data ke Firestore - KOPDES JAJAR
Jalankan: python upload_dummy_data.py
Pastikan file serviceaccount.json ada di folder yang sama
"""

import firebase_admin
from firebase_admin import credentials, firestore
import random
from datetime import datetime, timedelta

# Init Firebase
cred = credentials.Certificate("kopdes-jajar-6d545-firebase-adminsdk-fbsvc-6dbc4c7f89.json")
firebase_admin.initialize_app(cred)
db = firestore.client()

def epoch_ms(dt): return int(dt.timestamp() * 1000)
def rand_time(date): return datetime(date.year, date.month, date.day, random.randint(8,17), random.randint(0,59))

# ===================== PRODUCTS =====================
products = [
    {"id": 1,  "name": "Beras 5kg",        "category": "Sembako",         "price": 55000,  "purchasePrice": 42000, "stock": 150, "minimumStock": 30, "barcode": "8999999001"},
    {"id": 2,  "name": "Minyak Goreng",    "category": "Sembako",         "price": 15000,  "purchasePrice": 11000, "stock": 80,  "minimumStock": 20, "barcode": "8999999002"},
    {"id": 3,  "name": "Gula Pasir",       "category": "Sembako",         "price": 18000,  "purchasePrice": 13000, "stock": 60,  "minimumStock": 15, "barcode": "8999999003"},
    {"id": 4,  "name": "Telur Ayam",       "category": "Sembako",         "price": 28000,  "purchasePrice": 22000, "stock": 5,   "minimumStock": 20, "barcode": "8999999004"},
    {"id": 5,  "name": "Tepung Terigu",    "category": "Sembako",         "price": 12000,  "purchasePrice": 9000,  "stock": 3,   "minimumStock": 10, "barcode": "8999999005"},
    {"id": 6,  "name": "Indomie Goreng",   "category": "Sembako",         "price": 3500,   "purchasePrice": 2500,  "stock": 200, "minimumStock": 50, "barcode": "8999999006"},
    {"id": 7,  "name": "Rokok Surya",      "category": "Rokok",           "price": 27000,  "purchasePrice": 22000, "stock": 40,  "minimumStock": 10, "barcode": "8999999007"},
    {"id": 8,  "name": "Rokok Marlboro",   "category": "Rokok",           "price": 35000,  "purchasePrice": 28000, "stock": 25,  "minimumStock": 10, "barcode": "8999999008"},
    {"id": 9,  "name": "Pupuk NPK",        "category": "Pupuk",           "price": 120000, "purchasePrice": 95000, "stock": 8,   "minimumStock": 15, "barcode": "8999999009"},
    {"id": 10, "name": "Shampo Sunsilk",   "category": "Kebutuhan Rumah", "price": 18000,  "purchasePrice": 13000, "stock": 45,  "minimumStock": 15, "barcode": "8999999010"},
    {"id": 11, "name": "Sabun Lifebuoy",   "category": "Kebutuhan Rumah", "price": 5000,   "purchasePrice": 3500,  "stock": 100, "minimumStock": 20, "barcode": "8999999011"},
    {"id": 12, "name": "Deterjen Rinso",   "category": "Kebutuhan Rumah", "price": 22000,  "purchasePrice": 17000, "stock": 40,  "minimumStock": 15, "barcode": "8999999012"},
    {"id": 13, "name": "Air Mineral Aqua", "category": "Minuman",         "price": 4000,   "purchasePrice": 2800,  "stock": 120, "minimumStock": 30, "barcode": "8999999013"},
    {"id": 14, "name": "Kecap ABC",        "category": "Sembako",         "price": 12000,  "purchasePrice": 9000,  "stock": 35,  "minimumStock": 10, "barcode": "8999999014"},
    {"id": 15, "name": "Teh Botol Sosro",  "category": "Minuman",         "price": 5000,   "purchasePrice": 3500,  "stock": 80,  "minimumStock": 20, "barcode": "8999999015"},
]

print("📦 Uploading products...")
for p in products:
    doc = dict(p)
    doc["imagePath"] = None
    doc["expiredDateEpochMs"] = None
    doc["createdAtEpochMs"] = epoch_ms(datetime(2026, 1, 1))
    doc["updatedAt"] = epoch_ms(datetime(2026, 1, 1))
    db.collection("products").document(p["name"]).set(doc)
    print(f"  ✅ {p['name']}")
print(f"✅ {len(products)} products uploaded\n")

# ===================== SALES & MOVEMENTS =====================
kasir_users = [
    {"id": 2, "name": "Kasir"},
    {"id": 5, "name": "Alea"},
    {"id": 7, "name": "Sofwan"},
    {"id": 8, "name": "Zidan"},
]

print("💰 Generating sales Jan 1 - Jun 1, 2026...")
start = datetime(2026, 1, 1)
end   = datetime(2026, 6, 1)

sale_id     = 1
movement_id = 1
sales_count = 0
movements_count = 0

sales_batch     = db.batch()
movements_batch = db.batch()
sales_in_batch  = 0
moves_in_batch  = 0
MAX_BATCH       = 400

current = start
while current <= end:
    if current.weekday() == 6 and random.random() < 0.4:
        current += timedelta(days=1); continue
    if random.random() < 0.08:
        current += timedelta(days=1); continue

    for _ in range(random.randint(3, 8)):
        kasir     = random.choice(kasir_users)
        sale_time = rand_time(current)
        ts        = epoch_ms(sale_time)
        chosen    = random.sample(products, random.randint(1, 4))

        items    = []
        subtotal = 0
        for p in chosen:
            qty  = random.randint(1, 5)
            line = p["price"] * qty
            subtotal += line
            items.append({
                "id":          movement_id,
                "productId":   p["id"],
                "productName": p["name"],
                "unitPrice":   p["price"],
                "quantity":    qty,
                "lineTotal":   line,
            })
            movement_id += 1

        txid = f"TRX-{sale_time.strftime('%Y%m%d')}-{sale_id:04d}"
        sale_doc = {
            "id": sale_id, "transactionId": txid,
            "cashierId": kasir["id"], "subtotal": subtotal,
            "discount": 0, "tax": 0, "total": subtotal,
            "paymentMethod": "TUNAI", "status": "SUCCESS",
            "createdAtEpochMs": ts, "items": items,
        }
        sales_batch.set(db.collection("sales").document(str(sale_id)), sale_doc)
        sales_in_batch += 1
        sales_count    += 1

        for item in items:
            mov_doc = {
                "id": item["id"], "productId": item["productId"],
                "userId": kasir["id"], "type": "PENJUALAN",
                "quantityDelta": -item["quantity"],
                "note": f"saleId={sale_id}", "createdAtEpochMs": ts,
            }
            movements_batch.set(db.collection("movements").document(str(item["id"])), mov_doc)
            moves_in_batch  += 1
            movements_count += 1

        sale_id += 1

        if sales_in_batch >= MAX_BATCH:
            sales_batch.commit()
            sales_batch    = db.batch()
            sales_in_batch = 0
            print(f"  💾 {sales_count} sales committed...")

        if moves_in_batch >= MAX_BATCH:
            movements_batch.commit()
            movements_batch = db.batch()
            moves_in_batch  = 0
            print(f"  💾 {movements_count} movements committed...")

    current += timedelta(days=1)

# Commit sisa
if sales_in_batch > 0:
    sales_batch.commit()
if moves_in_batch > 0:
    movements_batch.commit()

print(f"\n🎉 SELESAI!")
print(f"   ✅ Products  : {len(products)}")
print(f"   ✅ Sales     : {sales_count}")
print(f"   ✅ Movements : {movements_count}")
