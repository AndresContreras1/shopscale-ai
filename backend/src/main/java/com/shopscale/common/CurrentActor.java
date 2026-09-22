package com.shopscale.common;

/**
 * Who is performing the current operation, stored in ledgers and audit trails.
 */
public final class CurrentActor {

    private CurrentActor() {
    }

    public static String name() {
        return "system";
    }
}
