package com.shopscale.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FlashSaleRequest(
        @NotNull Long productId,
        @NotNull @Min(1) @Max(500) Integer buyers,
        @NotNull @Min(1) @Max(10) Integer unitsPerBuyer) {
}
