package co.gamestore.security;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The TOTP algorithm of RFC 6238, which is what an authenticator app computes.
 *
 * <p>Both sides hold the same secret. The current 30 second interval is hashed with it, and six
 * digits are taken from the result. Nothing travels between the app and the server, so there is no
 * code to intercept in transit and nothing to phish by relaying a message.
 *
 * <p>Codes from the neighbouring intervals are accepted because clocks drift and people type slowly.
 * One step either way is the usual tolerance: wider would multiply the number of valid codes.
 */
public final class TimeBasedOneTimePassword {

    public static final Duration STEP = Duration.ofSeconds(30);
    public static final int DIGITS = 6;
    private static final int TOLERANCE_STEPS = 1;
    private static final String ALGORITHM = "HmacSHA1";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TimeBasedOneTimePassword() {
    }

    /** 160 bits, the size RFC 4226 recommends for a HMAC-SHA1 secret. */
    public static String newSecret() {
        byte[] secret = new byte[20];
        RANDOM.nextBytes(secret);
        return Base32.encode(secret);
    }

    public static String codeAt(String base32Secret, Instant when) {
        return compute(Base32.decode(base32Secret), when.getEpochSecond() / STEP.toSeconds());
    }

    /** True when the code matches this interval or one step either side of it. */
    public static boolean matches(String base32Secret, String code, Instant when) {
        if (code == null || code.length() != DIGITS) {
            return false;
        }
        byte[] secret = Base32.decode(base32Secret);
        long counter = when.getEpochSecond() / STEP.toSeconds();
        boolean matched = false;
        for (long step = -TOLERANCE_STEPS; step <= TOLERANCE_STEPS; step++) {
            // No early exit: the loop always runs the same number of times so the answer does not
            // leak which interval matched through how long the check took.
            matched |= constantTimeEquals(compute(secret, counter + step), code);
        }
        return matched;
    }

    /** The URI an authenticator app reads from a QR code. */
    public static String provisioningUri(String issuer, String account, String base32Secret) {
        return "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d".formatted(
                encode(issuer), encode(account), base32Secret, encode(issuer), DIGITS, STEP.toSeconds());
    }

    private static String compute(byte[] secret, long counter) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = (hash[offset] & 0x7F) << 24
                    | (hash[offset + 1] & 0xFF) << 16
                    | (hash[offset + 2] & 0xFF) << 8
                    | (hash[offset + 3] & 0xFF);
            return String.format("%0" + DIGITS + "d", binary % (int) Math.pow(10, DIGITS));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA1 is required by RFC 6238", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int difference = 0;
        for (int i = 0; i < a.length(); i++) {
            difference |= a.charAt(i) ^ b.charAt(i);
        }
        return difference == 0;
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
