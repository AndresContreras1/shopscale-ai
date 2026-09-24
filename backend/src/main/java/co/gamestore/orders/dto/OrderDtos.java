package co.gamestore.orders.dto;

import co.gamestore.orders.Order;
import co.gamestore.orders.OrderItem;
import co.gamestore.orders.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CheckoutRequest(@NotEmpty @Size(max = 50) List<@Valid CheckoutLine> items) {
    }

    public record CheckoutLine(@NotNull Long productId, @NotNull @Min(1) @Max(20) Integer quantity) {
    }

    public record StatusChangeRequest(@NotNull OrderStatus status) {
    }

    public record OrderLineResponse(Long productId, String sku, String productName, BigDecimal unitPrice,
                                    int quantity, BigDecimal lineTotal) {

        static OrderLineResponse from(OrderItem i) {
            return new OrderLineResponse(i.getProduct().getId(), i.getSku(), i.getProductName(), i.getUnitPrice(),
                    i.getQuantity(), i.getLineTotal());
        }
    }

    public record OrderResponse(String orderNumber, String customerEmail, OrderStatus status, BigDecimal total,
                                int units, Instant createdAt, Instant expiresAt, Instant paidAt,
                                List<OrderLineResponse> items) {

        public static OrderResponse from(Order o) {
            return new OrderResponse(o.getOrderNumber(), o.getCustomerEmail(), o.getStatus(), o.getTotal(),
                    o.unitCount(), o.getCreatedAt(), o.getExpiresAt(), o.getPaidAt(),
                    o.getItems().stream().map(OrderLineResponse::from).toList());
        }
    }
}
