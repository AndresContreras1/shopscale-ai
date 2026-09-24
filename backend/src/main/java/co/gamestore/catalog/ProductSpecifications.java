package co.gamestore.catalog;

import java.math.BigDecimal;
import org.springframework.data.jpa.domain.Specification;

/**
 * Composable filters for the product search. A filter returns an unrestricted specification when its
 * value is empty, so the storefront and the admin panel share one endpoint. Spring Data JPA 4 rejects
 * null specifications, so {@code Specification.unrestricted()} replaces the old null convention.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> matchesText(String text) {
        if (text == null || text.isBlank()) {
            return Specification.unrestricted();
        }
        String like = "%" + text.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), like),
                cb.like(cb.lower(root.get("sku")), like),
                cb.like(cb.lower(root.get("brand")), like));
    }

    public static Specification<Product> inCategory(Long categoryId) {
        return categoryId == null ? Specification.unrestricted()
                : (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Product> priceBetween(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> {
            if (min != null && max != null) {
                return cb.between(root.get("price"), min, max);
            }
            return min != null ? cb.greaterThanOrEqualTo(root.get("price"), min)
                    : cb.lessThanOrEqualTo(root.get("price"), max);
        };
    }

    public static Specification<Product> hasStatus(ProductStatus status) {
        return status == null ? Specification.unrestricted()
                : (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
