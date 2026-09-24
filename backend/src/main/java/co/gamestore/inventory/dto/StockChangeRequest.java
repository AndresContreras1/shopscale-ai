package co.gamestore.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * For receipts the quantity must be positive; for adjustments it is signed (negative removes units).
 */
public record StockChangeRequest(
        @NotNull Integer quantity,
        @NotBlank @Size(max = 200) String reason,
        @Size(max = 60) String reference) {
}
