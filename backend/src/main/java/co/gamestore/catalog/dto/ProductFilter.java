package co.gamestore.catalog.dto;

import co.gamestore.catalog.ProductStatus;
import java.math.BigDecimal;

public record ProductFilter(String q, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice,
                            ProductStatus status) {
}
