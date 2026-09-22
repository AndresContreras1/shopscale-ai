package com.shopscale.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shopscale.catalog.ProductRepository;
import com.shopscale.common.BusinessException;
import com.shopscale.common.NotFoundException;
import com.shopscale.inventory.InventoryService;
import com.shopscale.orders.dto.OrderDtos.CheckoutLine;
import com.shopscale.orders.dto.OrderDtos.CheckoutRequest;
import com.shopscale.orders.dto.OrderDtos.OrderResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OrderFlowTest {

    static final String CUSTOMER = "customer@shopscale.dev";

    @Autowired
    OrderService orderService;

    @Autowired
    InventoryService inventoryService;

    @Autowired
    ProductRepository productRepository;

    Long productId;

    @BeforeEach
    void setUp() {
        productId = productRepository.findBySku("HOME-APP-002").orElseThrow().getId();
    }

    @Test
    void checkoutReservesAndPaymentCommitsStock() {
        var before = inventoryService.find(productId);

        OrderResponse order = orderService.checkout(CUSTOMER, checkout(2));
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(inventoryService.find(productId).reserved()).isEqualTo(before.reserved() + 2);
        assertThat(inventoryService.find(productId).onHand()).isEqualTo(before.onHand());

        OrderResponse paid = orderService.pay(order.orderNumber(), CUSTOMER, false);
        assertThat(paid.status()).isEqualTo(OrderStatus.PAID);
        var after = inventoryService.find(productId);
        assertThat(after.onHand()).isEqualTo(before.onHand() - 2);
        assertThat(after.reserved()).isEqualTo(before.reserved());
    }

    @Test
    void cancelAndExpiryReleaseTheReservation() {
        int available = inventoryService.find(productId).available();

        OrderResponse cancelled = orderService.checkout(CUSTOMER, checkout(1));
        orderService.cancel(cancelled.orderNumber(), CUSTOMER, false);
        assertThat(inventoryService.find(productId).available()).isEqualTo(available);

        OrderResponse abandoned = orderService.checkout(CUSTOMER, checkout(1));
        orderService.expire(abandoned.orderNumber());
        assertThat(orderService.find(abandoned.orderNumber(), CUSTOMER, false).status()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(inventoryService.find(productId).available()).isEqualTo(available);
    }

    @Test
    void customersOnlySeeTheirOwnOrders() {
        OrderResponse order = orderService.checkout(CUSTOMER, checkout(1));
        assertThatThrownBy(() -> orderService.find(order.orderNumber(), "someone@else.com", false))
                .isInstanceOf(NotFoundException.class);
        assertThat(orderService.find(order.orderNumber(), "admin@shopscale.dev", true).orderNumber())
                .isEqualTo(order.orderNumber());
    }

    @Test
    void invalidTransitionsAreRejected() {
        OrderResponse order = orderService.checkout(CUSTOMER, checkout(1));
        assertThatThrownBy(() -> orderService.changeStatus(order.orderNumber(), OrderStatus.DELIVERED))
                .isInstanceOf(BusinessException.class);
    }

    private CheckoutRequest checkout(int quantity) {
        return new CheckoutRequest(List.of(new CheckoutLine(productId, quantity)));
    }
}
