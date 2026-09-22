package com.shopscale.inventory.dto;

import com.shopscale.inventory.InventoryItem;
import java.time.Instant;

public record InventoryResponse(
        Long productId,
        String sku,
        String productName,
        int onHand,
        int reserved,
        int available,
        int reorderPoint,
        boolean lowStock,
        Instant updatedAt) {

    public static InventoryResponse from(InventoryItem i) {
        return new InventoryResponse(i.getProduct().getId(), i.getProduct().getSku(), i.getProduct().getName(),
                i.getOnHand(), i.getReserved(), i.getAvailable(), i.getReorderPoint(), i.isLowStock(),
                i.getUpdatedAt());
    }
}
