package com.shopscale.orders;

import java.util.Set;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    /** Not paid in time: the reservation was released automatically. */
    EXPIRED;

    /** Allowed transitions of the order lifecycle. */
    public boolean canMoveTo(OrderStatus next) {
        return switch (this) {
            case PENDING_PAYMENT -> Set.of(PAID, CANCELLED, EXPIRED).contains(next);
            case PAID -> next == SHIPPED;
            case SHIPPED -> next == DELIVERED;
            case DELIVERED, CANCELLED, EXPIRED -> false;
        };
    }
}
