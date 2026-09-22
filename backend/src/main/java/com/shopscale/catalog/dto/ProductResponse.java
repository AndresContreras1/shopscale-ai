package com.shopscale.catalog.dto;

import com.shopscale.catalog.Product;
import com.shopscale.catalog.ProductStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        String brand,
        Long categoryId,
        String categoryName,
        BigDecimal price,
        BigDecimal compareAtPrice,
        String imageUrl,
        ProductStatus status,
        Instant updatedAt) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(), p.getBrand(),
                p.getCategory().getId(), p.getCategory().getName(), p.getPrice(), p.getCompareAtPrice(),
                p.getImageUrl(), p.getStatus(), p.getUpdatedAt());
    }
}
