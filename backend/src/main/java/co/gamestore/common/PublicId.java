package co.gamestore.common;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * Public identifiers, as UUID version 7 (RFC 9562).
 *
 * <p>Rows keep a {@code BIGINT} identity for joins, but nothing outside the system ever sees it: a
 * sequential number in a URL tells a stranger how many orders the store has and lets them walk
 * through other people's records by counting. A UUID does not.
 *
 * <p>Version 7 starts with the timestamp in milliseconds, so ids sort by creation time and land next
 * to each other in the index instead of scattering random pages, which is what made UUIDv4 primary
 * keys slow. PostgreSQL 18 has a native {@code uuidv7()}; until then it is generated here.
 */
public final class PublicId {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long TIMESTAMP_MASK = 0xFFFFFFFFFFFFL;

    private PublicId() {
    }

    public static UUID next() {
        return from(System.currentTimeMillis());
    }

    static UUID from(long epochMilli) {
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);

        long mostSignificant = (epochMilli & TIMESTAMP_MASK) << 16
                | 0x7000L                                   // version 7
                | (random[0] & 0x0FL) << 8 | (random[1] & 0xFFL);

        long leastSignificant = 0x8000000000000000L;        // variant 10
        for (int i = 2; i < 10; i++) {
            leastSignificant |= (random[i] & 0xFFL) << (8 * (9 - i));
        }
        leastSignificant &= 0xBFFFFFFFFFFFFFFFL;            // keep the variant bits intact

        return new UUID(mostSignificant, leastSignificant);
    }

    /** The creation instant carried inside a version 7 id. */
    public static Instant timestampOf(UUID id) {
        if (id.version() != 7) {
            throw new IllegalArgumentException("Not a UUID version 7: " + id);
        }
        return Instant.ofEpochMilli(id.getMostSignificantBits() >>> 16 & TIMESTAMP_MASK);
    }
}
