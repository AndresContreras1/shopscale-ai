package co.gamestore.orders;

import co.gamestore.catalog.Product;
import co.gamestore.common.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "ux_orders_number", columnList = "order_number", unique = true),
        @Index(name = "ix_orders_customer", columnList = "customer_email"),
        @Index(name = "ix_orders_status_created", columnList = "status, created_at")
})
@Getter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String orderNumber;

    @Column(nullable = false, length = 120)
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant expiresAt;

    private Instant paidAt;

    /** Two instances expiring or paying the same order at once: only one of them wins. */
    @Version
    private Long version;

    public Order(String orderNumber, String customerEmail, Instant createdAt, Instant expiresAt) {
        this.orderNumber = orderNumber;
        this.customerEmail = customerEmail;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public void addItem(Product product, int quantity) {
        OrderItem item = new OrderItem(this, product, quantity);
        items.add(item);
        total = total.add(item.getLineTotal());
    }

    public void moveTo(OrderStatus next, Instant when) {
        if (!status.canMoveTo(next)) {
            throw new BusinessException("Order " + orderNumber + " cannot go from " + status + " to " + next);
        }
        status = next;
        if (next == OrderStatus.PAID) {
            paidAt = when;
        }
    }

    public int unitCount() {
        return items.stream().mapToInt(OrderItem::getQuantity).sum();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
