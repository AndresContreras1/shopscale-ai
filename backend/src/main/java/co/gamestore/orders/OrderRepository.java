package co.gamestore.orders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findByOrderNumber(String orderNumber);

    Page<Order> findByCustomerEmailOrderByCreatedAtDesc(String email, Pageable pageable);

    java.util.List<Order> findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    /** Keeps the order, drops the person: the invoice must survive, the identity need not. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "update Order o set o.customerEmail = :pseudonym where lower(o.customerEmail) = lower(:email)")
    int anonymizeCustomer(@org.springframework.data.repository.query.Param("email") String email,
                          @org.springframework.data.repository.query.Param("pseudonym") String pseudonym);

    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("select o.orderNumber from Order o where o.status = co.gamestore.orders.OrderStatus.PENDING_PAYMENT and o.expiresAt < :now")
    List<String> findExpiredNumbers(Instant now);
}
