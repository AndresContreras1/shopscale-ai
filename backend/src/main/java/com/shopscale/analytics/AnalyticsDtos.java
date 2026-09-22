package com.shopscale.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public enum StockHealth {
        /** Nothing left to sell. */
        STOCKOUT,
        /** Will run out before a new purchase order can arrive. */
        CRITICAL,
        /** Below the reorder point or less than a month of cover. */
        LOW,
        HEALTHY,
        /** More than three months of stock: cash tied up in the warehouse. */
        OVERSTOCK,
        /** Stock available but no sales in the period. */
        NO_SALES
    }

    public record ProductInsight(Long productId, String sku, String name, String category, BigDecimal price,
                                 int available, int reorderPoint, long unitsSold30d, BigDecimal revenue30d,
                                 double dailyVelocity, Double daysOfCover, int suggestedReorderQty,
                                 StockHealth health) {
    }

    public record DailySales(LocalDate date, long orders, BigDecimal revenue) {
    }

    public record CategoryShare(String category, long units, BigDecimal revenue) {
    }

    public record SalesKpis(BigDecimal revenue30d, BigDecimal revenuePrev30d, double revenueGrowthPct,
                            long orders30d, long ordersPrev30d, BigDecimal averageOrderValue,
                            long pendingPaymentOrders) {
    }

    public record InventoryKpis(int activeProducts, long stockouts, long critical, long low, long overstock,
                                long noSales, BigDecimal inventoryValue) {
    }

    public record Dashboard(SalesKpis sales, InventoryKpis inventory, List<DailySales> dailySales,
                            List<CategoryShare> categories, List<ProductInsight> topSellers,
                            List<ProductInsight> restockNow, List<ProductInsight> slowMovers) {
    }
}
