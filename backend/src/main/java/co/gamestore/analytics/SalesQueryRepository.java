package co.gamestore.analytics;

import co.gamestore.orders.Order;
import co.gamestore.orders.OrderStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Read-only aggregate queries for dashboards and reports. The database does the grouping, so only a
 * few rows travel to the application regardless of how many orders exist.
 */
public interface SalesQueryRepository extends Repository<Order, Long> {

    @Query("""
            select new co.gamestore.analytics.ProductSales(i.product.id, sum(i.quantity), sum(i.lineTotal))
              from OrderItem i
             where i.order.status in :statuses and i.order.createdAt >= :since
             group by i.product.id
            """)
    List<ProductSales> salesByProduct(Collection<OrderStatus> statuses, Instant since);

    @Query("""
            select new co.gamestore.analytics.CategorySales(i.product.category.name, sum(i.quantity), sum(i.lineTotal))
              from OrderItem i
             where i.order.status in :statuses and i.order.createdAt >= :since
             group by i.product.category.name
             order by sum(i.lineTotal) desc
            """)
    List<CategorySales> salesByCategory(Collection<OrderStatus> statuses, Instant since);

    @Query("""
            select new co.gamestore.analytics.OrderPoint(o.createdAt, o.total)
              from Order o
             where o.status in :statuses and o.createdAt >= :since
            """)
    List<OrderPoint> orderPoints(Collection<OrderStatus> statuses, Instant since);

    @Query("select count(o) from Order o where o.status = :status")
    long countByStatus(OrderStatus status);
}
