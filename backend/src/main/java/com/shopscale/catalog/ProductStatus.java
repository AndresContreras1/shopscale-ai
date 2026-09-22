package com.shopscale.catalog;

public enum ProductStatus {
    /** Visible and purchasable in the storefront. */
    ACTIVE,
    /** Being prepared by the catalog team, not visible to customers. */
    DRAFT,
    /** Soft-deleted: kept for order history and reports. */
    ARCHIVED
}
