package co.gamestore.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues and redeems the single-use tokens that back email verification and password reset. */
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    /** 256 bits from a cryptographic source: not guessable, and not worth rate limiting on its own. */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthTokenRepository tokens;

    /**
     * @return the plain token, which exists only here and in the message. The database keeps its hash.
     */
    @Transactional
    public String issue(User user, AuthTokenPurpose purpose, Duration validity) {
        Instant now = Instant.now();
        tokens.retireOutstanding(user.getId(), purpose, now);

        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tokens.save(new AuthToken(user, purpose, hash(token), now.plus(validity)));
        return token;
    }

    /**
     * Redeems a token once. A token that is expired, already used or simply wrong is indistinguishable
     * from the outside: all three return empty.
     */
    @Transactional
    public Optional<User> redeem(String token, AuthTokenPurpose purpose) {
        Instant now = Instant.now();
        return tokens.findByTokenHashAndPurpose(hash(token), purpose)
                .filter(found -> found.isUsable(now))
                .map(found -> {
                    found.markUsed(now);
                    return found.getUser();
                });
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
