package com.shopscale.catalog.dto;

import com.shopscale.catalog.ProductStatus;
import java.math.BigDecimal;

public record ProductFilter(String q, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice,
                            ProductStatus status) {
}
