package com.shopscale.inventory;

import com.shopscale.audit.AuditService;
import com.shopscale.catalog.Product;
import com.shopscale.common.BusinessException;
import com.shopscale.common.CurrentActor;
import com.shopscale.common.NotFoundException;
import com.shopscale.common.PageResponse;
import com.shopscale.inventory.dto.InventoryResponse;
import com.shopscale.inventory.dto.MovementResponse;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every stock change goes through this service and leaves a {@link StockMovement} in the ledger.
 * Write methods join the caller's transaction so an order can reserve several products atomically.
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository movementRepository;
    private final EntityManager entityManager;
    private final AuditService auditService;

    @Transactional
    public InventoryItem initialize(Product product, int onHand, int reorderPoint) {
        InventoryItem item = inventoryRepository.save(new InventoryItem(product, onHand, reorderPoint));
        if (onHand > 0) {
            movementRepository.save(new StockMovement(item, MovementType.RECEIPT, onHand, "Initial stock", null,
                    CurrentActor.name()));
        }
        return item;
    }

    @Transactional
    public InventoryResponse receive(Long productId, int quantity, String reason, String reference) {
        InventoryItem item = getItem(productId);
        item.receive(quantity);
        record(item, MovementType.RECEIPT, quantity, reason, reference);
        return InventoryResponse.from(item);
    }

    @Transactional
    public InventoryResponse adjust(Long productId, int delta, String reason, String reference) {
        if (delta == 0) {
            throw new IllegalArgumentException("Adjustment quantity cannot be zero");
        }
        InventoryItem item = getItem(productId);
        item.adjust(delta);
        record(item, MovementType.ADJUSTMENT, delta, reason, reference);
        auditService.record("STOCK_ADJUSTED", "Product", productId, delta + " units: " + reason);
        return InventoryResponse.from(item);
    }

    @Transactional
    public void reserve(Long productId, int quantity, String reference) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        if (inventoryRepository.tryReserve(productId, quantity, Instant.now()) == 0) {
            InventoryItem item = getItem(productId);
            throw new BusinessException("Not enough stock for " + item.getProduct().getSku()
                    + ": requested " + quantity + ", available " + item.getAvailable());
        }
        // The UPDATE bypassed the persistence context, so reload the row before writing the ledger entry.
        InventoryItem item = getItem(productId);
        entityManager.refresh(item);
        record(item, MovementType.RESERVATION, quantity, "Reserved for order", reference);
    }

    @Transactional
    public void release(Long productId, int quantity, String reference) {
        InventoryItem item = getItem(productId);
        item.release(quantity);
        record(item, MovementType.RELEASE, quantity, "Reservation released", reference);
    }

    @Transactional
    public void commit(Long productId, int quantity, String reference) {
        InventoryItem item = getItem(productId);
        item.commit(quantity);
        record(item, MovementType.SALE, -quantity, "Order paid", reference);
    }

    @Transactional
    public InventoryResponse updateReorderPoint(Long productId, int reorderPoint) {
        InventoryItem item = getItem(productId);
        item.setReorderPoint(reorderPoint);
        auditService.record("REORDER_POINT_CHANGED", "Product", productId, "New reorder point " + reorderPoint);
        return InventoryResponse.from(item);
    }

    @Transactional(readOnly = true)
    public InventoryResponse find(Long productId) {
        return InventoryResponse.from(getItem(productId));
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryResponse> list(boolean lowStockOnly, Pageable pageable) {
        var page = lowStockOnly ? inventoryRepository.findLowStock(pageable) : inventoryRepository.findAllBy(pageable);
        return PageResponse.from(page.map(InventoryResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<MovementResponse> movements(Long productId, Pageable pageable) {
        return PageResponse.from(movementRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable)
                .map(MovementResponse::from));
    }

    /** Available units per product id, resolved with a single query for a whole page of products. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> availableFor(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return inventoryRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(i -> i.getProduct().getId(), InventoryItem::getAvailable));
    }

    private InventoryItem getItem(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new NotFoundException("Inventory for product", productId));
    }

    private void record(InventoryItem item, MovementType type, int quantity, String reason, String reference) {
        movementRepository.save(new StockMovement(item, type, quantity, reason, reference, CurrentActor.name()));
    }
}
