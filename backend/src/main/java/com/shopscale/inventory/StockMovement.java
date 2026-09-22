package com.shopscale.inventory;

import com.shopscale.catalog.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Append-only ledger (kardex) of every stock change. Rows are never updated or deleted, so the
 * current stock can always be explained and audited.
 */
@Entity
@Table(name = "stock_movements", indexes = {
        @Index(name = "ix_movements_product_created", columnList = "product_id, created_at"),
        @Index(name = "ix_movements_type_created", columnList = "type, created_at")
})
@Getter
@NoArgsConstructor
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    /** Signed quantity: positive adds units, negative removes them. */
    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private int onHandAfter;

    @Column(nullable = false)
    private int reservedAfter;

    @Column(length = 200)
    private String reason;

    /** External reference such as an order number or a supplier invoice. */
    @Column(length = 60)
    private String reference;

    @Column(nullable = false, length = 80)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    public StockMovement(InventoryItem item, MovementType type, int quantity, String reason, String reference,
                         String createdBy) {
        this(item, type, quantity, reason, reference, createdBy, Instant.now());
    }

    public StockMovement(InventoryItem item, MovementType type, int quantity, String reason, String reference,
                         String createdBy, Instant createdAt) {
        this.product = item.getProduct();
        this.type = type;
        this.quantity = quantity;
        this.onHandAfter = item.getOnHand();
        this.reservedAfter = item.getReserved();
        this.reason = reason;
        this.reference = reference;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }
}
