package co.gamestore.orders;

import co.gamestore.common.PersonalDataContributor;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the ordering module holds about a person, and what happens to it when they ask to be deleted.
 *
 * <p>The orders stay: they back an invoice the tax authority can ask for. The address on them is
 * replaced, so the rows no longer identify anybody.
 */
@Component
@RequiredArgsConstructor
public class OrderPersonalData implements PersonalDataContributor {

    private final OrderRepository orders;

    @Override
    public String section() {
        return "orders";
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> export(String email) {
        List<Map<String, Object>> rows = orders.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(email).stream()
                .map(order -> Map.<String, Object>of(
                        "orderNumber", order.getOrderNumber(),
                        "status", order.getStatus().name(),
                        "total", order.getTotal().toPlainString(),
                        "placedAt", order.getCreatedAt().toString()))
                .toList();
        return Map.of("count", rows.size(), "orders", rows);
    }

    @Override
    @Transactional
    public void anonymize(String email, String pseudonym) {
        orders.anonymizeCustomer(email, pseudonym);
    }
}
