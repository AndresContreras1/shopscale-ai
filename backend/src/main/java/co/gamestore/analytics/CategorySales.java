package co.gamestore.analytics;

import java.math.BigDecimal;

/** Row of an aggregate query (JPQL constructor expression). */
public record CategorySales(String category, Long units, BigDecimal revenue) {
}
