package com.shopscale.inventory;

import com.shopscale.catalog.Product;
import com.shopscale.common.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Stock of one product. Kept apart from {@link Product} on purpose: stock changes on every sale while
 * catalog data rarely changes, so they must not compete for the same row version.
 *
 * <ul>
 *   <li>onHand: physical units in the warehouse.</li>
 *   <li>reserved: units promised to orders that are not paid yet.</li>
 *   <li>available = onHand - reserved: what the storefront can still sell.</li>
 * </ul>
 */
@Entity
@Table(name = "inventory_items")
@Getter
@NoArgsConstructor
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", unique = true)
    private Product product;

    @Column(nullable = false)
    private int onHand;

    @Column(nullable = false)
    private int reserved;

    @Column(nullable = false)
    private int reorderPoint;

    /**
     * Optimistic locking: every UPDATE checks this value. If two buyers read the same row and both try
     * to take the last unit, the second UPDATE matches zero rows and fails instead of overselling.
     */
    @Version
    private Long version;

    @Column(nullable = false)
    private Instant updatedAt;

    public InventoryItem(Product product, int onHand, int reorderPoint) {
        this.product = product;
        this.onHand = onHand;
        this.reorderPoint = reorderPoint;
    }

    public int getAvailable() {
        return onHand - reserved;
    }

    public boolean isLowStock() {
        return getAvailable() <= reorderPoint;
    }

    void receive(int quantity) {
        requirePositive(quantity);
        onHand += quantity;
    }

    void adjust(int delta) {
        if (onHand + delta < reserved) {
            throw new BusinessException("Adjustment would leave less stock than is already reserved");
        }
        onHand += delta;
    }

    void reserve(int quantity) {
        requirePositive(quantity);
        if (getAvailable() < quantity) {
            throw new BusinessException("Not enough stock for " + product.getSku()
                    + ": requested " + quantity + ", available " + getAvailable());
        }
        reserved += quantity;
    }

    void release(int quantity) {
        requirePositive(quantity);
        reserved = Math.max(0, reserved - quantity);
    }

    /** Reserved units leave the warehouse: the sale is final. */
    void commit(int quantity) {
        requirePositive(quantity);
        if (reserved < quantity) {
            throw new BusinessException("Cannot commit more units than reserved for " + product.getSku());
        }
        reserved -= quantity;
        onHand -= quantity;
    }

    void setReorderPoint(int reorderPoint) {
        if (reorderPoint < 0) {
            throw new IllegalArgumentException("reorderPoint must be >= 0");
        }
        this.reorderPoint = reorderPoint;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
    }
}
