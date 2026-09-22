package com.shopscale.inventory.dto;

public record FlashSaleResult(
        String sku,
        int buyers,
        int unitsPerBuyer,
        int availableBefore,
        int successfulReservations,
        int rejectedNoStock,
        int unitsReserved,
        int minAvailableObserved,
        boolean oversold,
        long elapsedMs) {
}
