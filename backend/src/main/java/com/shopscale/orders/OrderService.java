package com.shopscale.orders;

import com.shopscale.audit.AuditService;
import com.shopscale.catalog.Product;
import com.shopscale.catalog.ProductRepository;
import com.shopscale.catalog.ProductStatus;
import com.shopscale.common.BusinessException;
import com.shopscale.common.NotFoundException;
import com.shopscale.common.PageResponse;
import com.shopscale.inventory.InventoryService;
import com.shopscale.orders.dto.OrderDtos.CheckoutLine;
import com.shopscale.orders.dto.OrderDtos.CheckoutRequest;
import com.shopscale.orders.dto.OrderDtos.OrderResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order lifecycle and its effect on stock:
 * checkout reserves, payment commits (units leave the warehouse), cancel/expiry releases.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final AuditService auditService;

    @Value("${app.orders.reservation-ttl:PT15M}")
    private Duration reservationTtl;

    /**
     * All lines are reserved in one transaction: either every product is reserved or none is.
     * Lines are processed in product id order so concurrent checkouts lock rows in the same order
     * and cannot deadlock each other.
     */
    @Transactional
    public OrderResponse checkout(String customerEmail, CheckoutRequest request) {
        Map<Long, Integer> quantities = new TreeMap<>();
        for (CheckoutLine line : request.items()) {
            quantities.merge(line.productId(), line.quantity(), Integer::sum);
        }

        Instant now = Instant.now();
        Order order = new Order(newOrderNumber(now), customerEmail, now, now.plus(reservationTtl));
        quantities.forEach((productId, quantity) -> {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new NotFoundException("Product", productId));
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new BusinessException("Product " + product.getSku() + " is not available for sale");
            }
            inventoryService.reserve(productId, quantity, order.getOrderNumber());
            order.addItem(product, quantity);
        });
        orderRepository.save(order);
        return OrderResponse.from(order);
    }

    /** Simulated payment gateway approval. */
    @Transactional
    public OrderResponse pay(String orderNumber, String requester, boolean isAdmin) {
        Order order = getOwned(orderNumber, requester, isAdmin);
        order.moveTo(OrderStatus.PAID, Instant.now());
        order.getItems().forEach(i -> inventoryService.commit(i.getProduct().getId(), i.getQuantity(), orderNumber));
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancel(String orderNumber, String requester, boolean isAdmin) {
        Order order = getOwned(orderNumber, requester, isAdmin);
        order.moveTo(OrderStatus.CANCELLED, Instant.now());
        releaseAll(order);
        auditService.record("ORDER_CANCELLED", "Order", orderNumber, null);
        return OrderResponse.from(order);
    }

    /** Back-office transitions: PAID to SHIPPED to DELIVERED. */
    @Transactional
    public OrderResponse changeStatus(String orderNumber, OrderStatus next) {
        if (next != OrderStatus.SHIPPED && next != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Use the pay or cancel endpoints for " + next);
        }
        Order order = getOrder(orderNumber);
        order.moveTo(next, Instant.now());
        auditService.record("ORDER_" + next, "Order", orderNumber, null);
        return OrderResponse.from(order);
    }

    /** Called by the expiration job for each unpaid order past its deadline. */
    @Transactional
    public void expire(String orderNumber) {
        Order order = getOrder(orderNumber);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }
        order.moveTo(OrderStatus.EXPIRED, Instant.now());
        releaseAll(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse find(String orderNumber, String requester, boolean isAdmin) {
        return OrderResponse.from(getOwned(orderNumber, requester, isAdmin));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> mine(String email, Pageable pageable) {
        return PageResponse.from(orderRepository.findByCustomerEmailOrderByCreatedAtDesc(email, pageable)
                .map(OrderResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> all(OrderStatus status, Pageable pageable) {
        var page = status == null ? orderRepository.findAllByOrderByCreatedAtDesc(pageable)
                : orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return PageResponse.from(page.map(OrderResponse::from));
    }

    private void releaseAll(Order order) {
        order.getItems().forEach(i ->
                inventoryService.release(i.getProduct().getId(), i.getQuantity(), order.getOrderNumber()));
    }

    private Order getOwned(String orderNumber, String requester, boolean isAdmin) {
        Order order = getOrder(orderNumber);
        if (!isAdmin && !order.getCustomerEmail().equalsIgnoreCase(requester)) {
            // Answer 404 instead of 403 so customers cannot probe which order numbers exist.
            throw new NotFoundException("Order", orderNumber);
        }
        return order;
    }

    private Order getOrder(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new NotFoundException("Order", orderNumber));
    }

    static String newOrderNumber(Instant when) {
        String day = LocalDate.ofInstant(when, ZoneOffset.UTC).format(DAY);
        return "SS-" + day + "-" + Long.toString(ThreadLocalRandom.current().nextLong(36L * 36 * 36 * 36 * 36 * 36), 36)
                .toUpperCase();
    }
}
