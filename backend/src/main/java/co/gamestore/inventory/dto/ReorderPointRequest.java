package co.gamestore.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReorderPointRequest(@NotNull @Min(0) Integer reorderPoint) {
}
