package com.shopscale.seed;

import java.math.BigDecimal;
import java.util.List;

/**
 * Demo catalog: realistic products spread across categories and price ranges.
 */
final class SeedCatalog {

    private SeedCatalog() {
    }

    record SeedCategory(String name, String slug, String description) {
    }

    record SeedProduct(String sku, String name, String brand, String categorySlug, String price, String compareAt) {

        BigDecimal priceValue() {
            return new BigDecimal(price);
        }

        BigDecimal compareAtValue() {
            return compareAt == null ? null : new BigDecimal(compareAt);
        }
    }

    static final List<SeedCategory> CATEGORIES = List.of(
            new SeedCategory("Electronics", "electronics", "Phones, audio, computers and accessories"),
            new SeedCategory("Home & Kitchen", "home-kitchen", "Appliances, cookware and home essentials"),
            new SeedCategory("Fashion", "fashion", "Clothing, shoes and accessories"),
            new SeedCategory("Sports & Outdoors", "sports", "Fitness equipment and outdoor gear"),
            new SeedCategory("Beauty & Health", "beauty", "Skincare, personal care and wellness"),
            new SeedCategory("Books & Office", "books-office", "Books, stationery and office supplies"));

    static final List<SeedProduct> PRODUCTS = List.of(
            new SeedProduct("ELEC-PHN-001", "Smartphone Nova X 128GB", "Nova", "electronics", "499.00", "579.00"),
            new SeedProduct("ELEC-PHN-002", "Smartphone Nova Lite 64GB", "Nova", "electronics", "249.00", null),
            new SeedProduct("ELEC-AUD-001", "Wireless Earbuds Pulse Pro", "Pulse", "electronics", "89.90", "119.90"),
            new SeedProduct("ELEC-AUD-002", "Noise Cancelling Headphones Q7", "Pulse", "electronics", "179.00", null),
            new SeedProduct("ELEC-CMP-001", "Laptop Aero 14 Ryzen 7", "Aero", "electronics", "899.00", "999.00"),
            new SeedProduct("ELEC-CMP-002", "Mechanical Keyboard K84 RGB", "Keyworks", "electronics", "69.00", null),
            new SeedProduct("ELEC-CMP-003", "Wireless Mouse Glide M2", "Keyworks", "electronics", "24.90", null),
            new SeedProduct("ELEC-ACC-001", "USB-C Fast Charger 65W", "Voltix", "electronics", "34.90", null),
            new SeedProduct("ELEC-ACC-002", "Power Bank 20000mAh", "Voltix", "electronics", "39.90", "49.90"),
            new SeedProduct("ELEC-WEA-001", "Smartwatch Fit 3", "Nova", "electronics", "129.00", null),

            new SeedProduct("HOME-APP-001", "Air Fryer 5.5L Digital", "Cookly", "home-kitchen", "89.00", "109.00"),
            new SeedProduct("HOME-APP-002", "Espresso Machine Barista One", "Cookly", "home-kitchen", "219.00", null),
            new SeedProduct("HOME-APP-003", "Robot Vacuum Clean S5", "Homebot", "home-kitchen", "259.00", "299.00"),
            new SeedProduct("HOME-KIT-001", "Non-stick Cookware Set 10 pcs", "Cookly", "home-kitchen", "119.00", null),
            new SeedProduct("HOME-KIT-002", "Chef Knife 8 inch", "Edge", "home-kitchen", "45.00", null),
            new SeedProduct("HOME-DEC-001", "LED Desk Lamp Dimmable", "Lumo", "home-kitchen", "29.90", null),
            new SeedProduct("HOME-DEC-002", "Memory Foam Pillow", "Dreamy", "home-kitchen", "35.00", "42.00"),

            new SeedProduct("FASH-SHO-001", "Running Shoes Stride 2", "Stride", "fashion", "74.90", "89.90"),
            new SeedProduct("FASH-SHO-002", "Leather Sneakers Urban", "Urbano", "fashion", "95.00", null),
            new SeedProduct("FASH-CLO-001", "Denim Jacket Classic", "Urbano", "fashion", "59.90", null),
            new SeedProduct("FASH-CLO-002", "Cotton T-Shirt Basic Pack x3", "Basics", "fashion", "24.90", null),
            new SeedProduct("FASH-CLO-003", "Hoodie Oversize Fleece", "Basics", "fashion", "39.90", "49.90"),
            new SeedProduct("FASH-ACC-001", "Leather Wallet Slim", "Urbano", "fashion", "29.00", null),
            new SeedProduct("FASH-ACC-002", "Sunglasses Polarized Aviator", "Solaris", "fashion", "49.00", null),

            new SeedProduct("SPRT-FIT-001", "Adjustable Dumbbells 24kg", "IronCore", "sports", "189.00", "229.00"),
            new SeedProduct("SPRT-FIT-002", "Yoga Mat 6mm Non-slip", "Zenfit", "sports", "25.90", null),
            new SeedProduct("SPRT-FIT-003", "Resistance Bands Set", "Zenfit", "sports", "19.90", null),
            new SeedProduct("SPRT-OUT-001", "Camping Tent 4 Person", "TrailPeak", "sports", "139.00", null),
            new SeedProduct("SPRT-OUT-002", "Hydration Backpack 15L", "TrailPeak", "sports", "44.90", null),
            new SeedProduct("SPRT-CYC-001", "Bike Helmet Aero Vent", "Velo", "sports", "54.90", "64.90"),

            new SeedProduct("BEAU-SKN-001", "Vitamin C Serum 30ml", "Glowlab", "beauty", "22.90", null),
            new SeedProduct("BEAU-SKN-002", "Daily Moisturizer SPF 30", "Glowlab", "beauty", "18.90", null),
            new SeedProduct("BEAU-HAI-001", "Hair Dryer Ionic 2000W", "Silka", "beauty", "49.90", "59.90"),
            new SeedProduct("BEAU-PER-001", "Electric Toothbrush Sonic", "Brite", "beauty", "39.90", null),
            new SeedProduct("BEAU-WEL-001", "Whey Protein 1kg Vanilla", "Fuel", "beauty", "32.00", null),

            new SeedProduct("BOOK-BUS-001", "Book: Scaling Online Stores", "Northwind Press", "books-office", "27.00", null),
            new SeedProduct("BOOK-TEC-001", "Book: Clean APIs in Practice", "Northwind Press", "books-office", "34.00", null),
            new SeedProduct("OFFC-SUP-001", "Notebook A5 Dotted", "Papyr", "books-office", "8.90", null),
            new SeedProduct("OFFC-SUP-002", "Gel Pens Pack x12", "Papyr", "books-office", "9.90", null),
            new SeedProduct("OFFC-FUR-001", "Ergonomic Office Chair", "Sitwell", "books-office", "229.00", "269.00"));
}
