package com.shopscale.analytics;

import com.shopscale.analytics.AnalyticsDtos.CategoryShare;
import com.shopscale.analytics.AnalyticsDtos.DailySales;
import com.shopscale.analytics.AnalyticsDtos.Dashboard;
import com.shopscale.analytics.AnalyticsDtos.InventoryKpis;
import com.shopscale.analytics.AnalyticsDtos.ProductInsight;
import com.shopscale.analytics.AnalyticsDtos.SalesKpis;
import com.shopscale.analytics.AnalyticsDtos.StockHealth;
import com.shopscale.catalog.ProductStatus;
import com.shopscale.inventory.InventoryItem;
import com.shopscale.inventory.InventoryRepository;
import com.shopscale.orders.OrderStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns raw orders and stock into business indicators. These numbers are computed by code, not by
 * the AI: the AI only explains them, so reports never contain invented figures.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    static final Set<OrderStatus> SOLD = EnumSet.of(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED);
    private static final int PERIOD_DAYS = 30;
    /** Days a supplier takes to deliver a purchase order. */
    private static final int LEAD_TIME_DAYS = 7;
    private static final int SAFETY_DAYS = 7;
    /** Stock to have after restocking, in days of sales. */
    private static final int TARGET_COVER_DAYS = 30;
    private static final int OVERSTOCK_DAYS = 90;

    private final SalesQueryRepository salesQueries;
    private final InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    public List<ProductInsight> productInsights() {
        Instant since = Instant.now().minus(Duration.ofDays(PERIOD_DAYS));
        Map<Long, ProductSales> sales = salesQueries.salesByProduct(SOLD, since).stream()
                .collect(Collectors.toMap(ProductSales::productId, Function.identity()));
        return inventoryRepository.findByProductStatus(ProductStatus.ACTIVE).stream()
                .map(item -> insight(item, sales.get(item.getProduct().getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public SalesKpis salesKpis() {
        Instant now = Instant.now();
        List<OrderPoint> last60 = salesQueries.orderPoints(SOLD, now.minus(Duration.ofDays(2L * PERIOD_DAYS)));
        Instant cut = now.minus(Duration.ofDays(PERIOD_DAYS));
        List<OrderPoint> current = last60.stream().filter(p -> !p.createdAt().isBefore(cut)).toList();
        List<OrderPoint> previous = last60.stream().filter(p -> p.createdAt().isBefore(cut)).toList();
        BigDecimal revenue = sum(current);
        BigDecimal prevRevenue = sum(previous);
        double growth = prevRevenue.signum() == 0 ? 0
                : revenue.subtract(prevRevenue).multiply(BigDecimal.valueOf(100))
                        .divide(prevRevenue, 1, RoundingMode.HALF_UP).doubleValue();
        BigDecimal aov = current.isEmpty() ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(current.size()), 2, RoundingMode.HALF_UP);
        return new SalesKpis(revenue, prevRevenue, growth, current.size(), previous.size(), aov,
                salesQueries.countByStatus(OrderStatus.PENDING_PAYMENT));
    }

    @Transactional(readOnly = true)
    public List<DailySales> dailySales() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Map<LocalDate, List<OrderPoint>> byDay = salesQueries
                .orderPoints(SOLD, Instant.now().minus(Duration.ofDays(PERIOD_DAYS)))
                .stream()
                .collect(Collectors.groupingBy(p -> LocalDate.ofInstant(p.createdAt(), ZoneOffset.UTC), TreeMap::new,
                        Collectors.toList()));
        return today.minusDays(PERIOD_DAYS - 1L).datesUntil(today.plusDays(1))
                .map(d -> {
                    List<OrderPoint> points = byDay.getOrDefault(d, List.of());
                    return new DailySales(d, points.size(), sum(points));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryShare> categories() {
        return salesQueries.salesByCategory(SOLD, Instant.now().minus(Duration.ofDays(PERIOD_DAYS))).stream()
                .map(c -> new CategoryShare(c.category(), c.units(), c.revenue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        List<ProductInsight> insights = productInsights();
        return new Dashboard(salesKpis(), inventoryKpis(insights), dailySales(), categories(),
                topSellers(insights, 5), restockNow(insights), slowMovers(insights));
    }

    public InventoryKpis inventoryKpis(List<ProductInsight> insights) {
        Map<StockHealth, Long> counts = insights.stream()
                .collect(Collectors.groupingBy(ProductInsight::health, Collectors.counting()));
        BigDecimal value = insights.stream()
                .map(i -> i.price().multiply(BigDecimal.valueOf(Math.max(i.available(), 0))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new InventoryKpis(insights.size(), counts.getOrDefault(StockHealth.STOCKOUT, 0L),
                counts.getOrDefault(StockHealth.CRITICAL, 0L), counts.getOrDefault(StockHealth.LOW, 0L),
                counts.getOrDefault(StockHealth.OVERSTOCK, 0L), counts.getOrDefault(StockHealth.NO_SALES, 0L), value);
    }

    public List<ProductInsight> topSellers(List<ProductInsight> insights, int limit) {
        return insights.stream().sorted(Comparator.comparing(ProductInsight::revenue30d).reversed()).limit(limit).toList();
    }

    /** Products that sell and will run out soon, most urgent first. */
    public List<ProductInsight> restockNow(List<ProductInsight> insights) {
        return insights.stream()
                .filter(i -> i.suggestedReorderQty() > 0)
                .sorted(Comparator.comparing((ProductInsight i) -> i.daysOfCover() == null ? 0 : i.daysOfCover()))
                .toList();
    }

    /** Stock that is not turning into cash, largest tied-up value first. */
    public List<ProductInsight> slowMovers(List<ProductInsight> insights) {
        return insights.stream()
                .filter(i -> i.health() == StockHealth.NO_SALES || i.health() == StockHealth.OVERSTOCK)
                .sorted(Comparator.comparing((ProductInsight i) -> i.price().multiply(BigDecimal.valueOf(i.available())))
                        .reversed())
                .toList();
    }

    private ProductInsight insight(InventoryItem item, ProductSales sales) {
        long units = sales == null ? 0 : sales.units();
        BigDecimal revenue = sales == null ? BigDecimal.ZERO : sales.revenue();
        double velocity = Math.round(units * 100.0 / PERIOD_DAYS) / 100.0;
        int available = item.getAvailable();
        Double cover = velocity > 0 ? Math.round(available / velocity * 10) / 10.0 : null;

        StockHealth health;
        if (available <= 0) {
            health = StockHealth.STOCKOUT;
        } else if (velocity == 0) {
            health = StockHealth.NO_SALES;
        } else if (cover < LEAD_TIME_DAYS) {
            health = StockHealth.CRITICAL;
        } else if (available <= item.getReorderPoint() || cover < TARGET_COVER_DAYS) {
            health = StockHealth.LOW;
        } else if (cover > OVERSTOCK_DAYS) {
            health = StockHealth.OVERSTOCK;
        } else {
            health = StockHealth.HEALTHY;
        }

        int reorder = 0;
        if (velocity > 0 && (health == StockHealth.STOCKOUT || health == StockHealth.CRITICAL || health == StockHealth.LOW)) {
            reorder = Math.max(0, (int) Math.ceil(velocity * (LEAD_TIME_DAYS + SAFETY_DAYS + TARGET_COVER_DAYS)) - available);
        }
        var product = item.getProduct();
        return new ProductInsight(product.getId(), product.getSku(), product.getName(), product.getCategory().getName(),
                product.getPrice(), available, item.getReorderPoint(), units, revenue, velocity, cover, reorder, health);
    }

    private static BigDecimal sum(List<OrderPoint> points) {
        return points.stream().map(OrderPoint::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
