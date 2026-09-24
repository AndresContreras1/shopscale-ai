package co.gamestore.security;

public enum Role {
    /** Full access: catalog, prices, inventory, reports, audit. */
    ADMIN,
    /** Warehouse staff: inventory only. */
    OPERATOR,
    /** Storefront customer: own orders only. */
    CUSTOMER
}
