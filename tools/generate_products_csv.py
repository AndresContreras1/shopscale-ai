"""Generates a product feed to test the bulk import.

    python tools/generate_products_csv.py 5000 > products.csv

Then upload it from Back office > Products > Import CSV.
"""
import random
import sys

CATEGORIES = {
    "electronics": ["Cable", "Adapter", "Speaker", "Headset", "Webcam", "Hub"],
    "home-kitchen": ["Mug", "Pan", "Blender", "Lamp", "Kettle", "Organizer"],
    "fashion": ["Cap", "Scarf", "Belt", "Socks", "Backpack", "Jacket"],
    "sports": ["Bottle", "Gloves", "Rope", "Ball", "Mat", "Band"],
    "beauty": ["Cream", "Shampoo", "Brush", "Serum", "Soap", "Lotion"],
    "books-office": ["Notebook", "Planner", "Marker", "Folder", "Stapler", "Desk Pad"],
}
BRANDS = ["Acme", "Northwind", "Contoso", "Globex", "Initech", "Umbrella"]


def main() -> None:
    count = int(sys.argv[1]) if len(sys.argv) > 1 else 1000
    random.seed(7)
    print("sku,name,brand,category_slug,price,stock")
    for i in range(1, count + 1):
        slug = random.choice(list(CATEGORIES))
        item = random.choice(CATEGORIES[slug])
        brand = random.choice(BRANDS)
        price = round(random.uniform(4, 250), 2)
        stock = random.randint(0, 300)
        print(f"FEED-{slug[:4].upper()}-{i:05d},{brand} {item} {i},{brand},{slug},{price},{stock}")


if __name__ == "__main__":
    main()
