package com.shopscale.catalog.dto;

import com.shopscale.catalog.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z0-9-]{3,40}$", message = "must be 3-40 uppercase letters, digits or dashes")
        String sku,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 2000) String description,
        @Size(max = 80) String brand,
        @NotNull Long categoryId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal compareAtPrice,
        @Size(max = 500) String imageUrl,
        ProductStatus status,
        @Min(0) Integer initialStock,
        @Min(0) Integer reorderPoint) {
}
