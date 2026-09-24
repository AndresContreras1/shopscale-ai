package co.gamestore.orders;

import co.gamestore.common.ConflictRetry;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Abandoned checkouts must not hold stock forever: unpaid orders past their deadline are expired and
 * their units return to available. Safe with several replicas: the order row version makes sure only
 * one instance expires each order.
 */
@Component
@RequiredArgsConstructor
public class ReservationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final ConflictRetry conflictRetry;

    @Scheduled(fixedDelayString = "${app.orders.expiry-check-interval:PT1M}")
    public void expireUnpaidOrders() {
        for (String orderNumber : orderRepository.findExpiredNumbers(Instant.now())) {
            try {
                conflictRetry.run(() -> orderService.expire(orderNumber));
                log.info("Order {} expired, stock released", orderNumber);
            } catch (RuntimeException ex) {
                log.warn("Could not expire order {}: {}", orderNumber, ex.getMessage());
            }
        }
    }
}
