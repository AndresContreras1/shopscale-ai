package co.gamestore.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Checked against the test vectors in RFC 6238 appendix B. If these pass, an authenticator app and
 * this code agree, which is the only interoperability that matters here.
 */
class TimeBasedOneTimePasswordTest {

    /** The RFC uses the ASCII secret "12345678901234567890", which is this in base32. */
    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void matchesTheReferenceVectors() {
        assertThat(TimeBasedOneTimePassword.codeAt(SECRET, Instant.ofEpochSecond(59))).isEqualTo("287082");
        assertThat(TimeBasedOneTimePassword.codeAt(SECRET, Instant.ofEpochSecond(1111111109))).isEqualTo("081804");
        assertThat(TimeBasedOneTimePassword.codeAt(SECRET, Instant.ofEpochSecond(1234567890))).isEqualTo("005924");
    }

    @Test
    void acceptsACodeFromTheNeighbouringIntervals() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        String previous = TimeBasedOneTimePassword.codeAt(SECRET, now.minusSeconds(30));
        String next = TimeBasedOneTimePassword.codeAt(SECRET, now.plusSeconds(30));

        assertThat(TimeBasedOneTimePassword.matches(SECRET, previous, now)).isTrue();
        assertThat(TimeBasedOneTimePassword.matches(SECRET, next, now)).isTrue();
    }

    @Test
    void refusesACodeFromTooFarAway() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        String longAgo = TimeBasedOneTimePassword.codeAt(SECRET, now.minusSeconds(300));

        assertThat(TimeBasedOneTimePassword.matches(SECRET, longAgo, now)).isFalse();
    }

    @Test
    void refusesAnythingThatIsNotSixDigits() {
        Instant now = Instant.now();

        assertThat(TimeBasedOneTimePassword.matches(SECRET, "12345", now)).isFalse();
        assertThat(TimeBasedOneTimePassword.matches(SECRET, null, now)).isFalse();
    }

    @Test
    void buildsAUriAnAuthenticatorAppCanRead() {
        String uri = TimeBasedOneTimePassword.provisioningUri("Game Store", "admin@gamestore.co", SECRET);

        assertThat(uri).startsWith("otpauth://totp/")
                .contains("secret=" + SECRET)
                .contains("digits=6")
                .contains("period=30");
    }

    @Test
    void generatesASecretOfTheSizeTheRfcRecommends() {
        String secret = TimeBasedOneTimePassword.newSecret();

        assertThat(Base32.decode(secret)).hasSize(20);
        assertThat(TimeBasedOneTimePassword.matches(secret, TimeBasedOneTimePassword.codeAt(secret, Instant.now()),
                Instant.now())).isTrue();
    }
}
