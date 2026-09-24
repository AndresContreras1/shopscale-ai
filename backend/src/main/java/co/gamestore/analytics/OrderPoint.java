package co.gamestore.analytics;

import java.math.BigDecimal;
import java.time.Instant;

/** Row of an aggregate query (JPQL constructor expression). */
public record OrderPoint(Instant createdAt, BigDecimal total) {
}
