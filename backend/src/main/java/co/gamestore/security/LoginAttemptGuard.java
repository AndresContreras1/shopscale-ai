package co.gamestore.security;

import co.gamestore.security.ratelimit.RateLimiter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limits failed sign-in attempts per account, which the per-address limit does not cover.
 *
 * <p>An attacker with a list of addresses walks straight past a per-IP limit. This counts failures
 * against the account itself, so guessing one password stops after a handful of tries no matter where
 * the requests come from.
 *
 * <p>The address is hashed before it becomes a key, so a dump of the cache is not a list of customers.
 */
@Component
public class LoginAttemptGuard {

    private final RateLimiter rateLimiter;
    private final int maxFailures;
    private final Duration window;

    public LoginAttemptGuard(RateLimiter rateLimiter,
                             @Value("${app.security.login.max-failures-per-account:8}") int maxFailures,
                             @Value("${app.security.login.failure-window:PT15M}") Duration window) {
        this.rateLimiter = rateLimiter;
        this.maxFailures = maxFailures;
        this.window = window;
    }

    /** True once the account has used up its failed attempts for the current window. Spends nothing. */
    public boolean isBlocked(String email) {
        return rateLimiter.remaining(key(email), maxFailures, window) <= 0;
    }

    /** Called after a wrong password, and only then, so a normal sign-in costs nothing. */
    public void recordFailure(String email) {
        rateLimiter.tryAcquire(key(email), maxFailures, window);
    }

    private String key(String email) {
        return "login:account:" + hash(email.trim().toLowerCase(Locale.ROOT));
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
