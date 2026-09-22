package com.shopscale.inventory;

import com.shopscale.catalog.ProductStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<InventoryItem, Long> {

    /**
     * Atomic reservation for the hot path (checkout, flash sales). The stock check and the increment run
     * in a single UPDATE, so the database itself guarantees that available stock never goes below zero,
     * without read-modify-write races or retry storms on popular products.
     *
     * @return 1 if the units were reserved, 0 if there was not enough stock
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update InventoryItem i
               set i.reserved = i.reserved + :qty, i.version = i.version + 1, i.updatedAt = :now
             where i.product.id = :productId and (i.onHand - i.reserved) >= :qty
            """)
    int tryReserve(@Param("productId") Long productId, @Param("qty") int qty, @Param("now") Instant now);

    @EntityGraph(attributePaths = "product")
    Optional<InventoryItem> findByProductId(Long productId);

    List<InventoryItem> findByProductIdIn(Collection<Long> productIds);

    @EntityGraph(attributePaths = {"product", "product.category"})
    List<InventoryItem> findByProductStatus(ProductStatus status);

    @EntityGraph(attributePaths = "product")
    Page<InventoryItem> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "product")
    @Query("select i from InventoryItem i where (i.onHand - i.reserved) <= i.reorderPoint")
    Page<InventoryItem> findLowStock(Pageable pageable);

    @Query("select count(i) from InventoryItem i where (i.onHand - i.reserved) <= i.reorderPoint")
    long countLowStock();

    @Query("select count(i) from InventoryItem i where (i.onHand - i.reserved) <= 0")
    long countOutOfStock();
}
