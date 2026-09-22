package com.shopscale.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Who is performing the current operation, stored in ledgers and audit trails.
 * Falls back to "system" for background jobs and startup tasks.
 */
public final class CurrentActor {

    private CurrentActor() {
    }

    public static String name() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "system";
        }
        return auth.getName();
    }
}
