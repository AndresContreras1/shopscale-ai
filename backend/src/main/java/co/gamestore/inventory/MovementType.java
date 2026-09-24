package co.gamestore.inventory;

public enum MovementType {
    /** Goods received from a supplier. */
    RECEIPT,
    /** Manual correction after a physical count, damage or loss. */
    ADJUSTMENT,
    /** Units held for an unpaid order. */
    RESERVATION,
    /** Reservation cancelled or expired: units return to available. */
    RELEASE,
    /** Paid order: units leave the warehouse. */
    SALE
}
