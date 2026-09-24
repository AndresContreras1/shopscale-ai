package co.gamestore.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Asks Have I Been Pwned whether a password appears in a known breach, without ever sending it.
 *
 * <p>The password is hashed with SHA-1 and only the first five characters of the hash leave this
 * process. The service answers with every hash that starts with those five characters, a few hundred
 * of them, and the match is done here. That is k-anonymity: the service cannot tell which of the
 * candidates was asked about, and it never sees the password.
 *
 * <p>SHA-1 is used because that is the corpus format, not as password storage. Passwords are stored
 * with BCrypt.
 *
 * <p>If the service is unreachable the registration goes through. A breach check that becomes an
 * outage is worse than a breach check that occasionally misses.
 */
@Component
@ConditionalOnProperty(name = "app.security.password.breach-check", havingValue = "true", matchIfMissing = true)
public class HibpBreachedPasswordChecker implements BreachedPasswordChecker {

    private static final Logger log = LoggerFactory.getLogger(HibpBreachedPasswordChecker.class);
    private static final int PREFIX_LENGTH = 5;

    private final RestClient http;

    public HibpBreachedPasswordChecker(@Qualifier("hibpRestClient") RestClient http) {
        this.http = http;
    }

    /** Short timeouts on purpose: a slow breach service must not hold up a registration. */
    static RestClient restClient(String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public boolean isBreached(String password) {
        String hash = sha1(password);
        String prefix = hash.substring(0, PREFIX_LENGTH);
        String suffix = hash.substring(PREFIX_LENGTH);
        try {
            String body = http.get().uri("/range/{prefix}", prefix)
                    .header("Add-Padding", "true")
                    .retrieve()
                    .body(String.class);
            return containsSuffix(body, suffix);
        } catch (RuntimeException e) {
            log.warn("Breach check unavailable, allowing the password: {}", e.getMessage());
            return false;
        }
    }

    private static boolean containsSuffix(String body, String suffix) {
        if (body == null) {
            return false;
        }
        for (String line : body.split("\\R")) {
            int separator = line.indexOf(':');
            String candidate = separator < 0 ? line : line.substring(0, separator);
            // A padded response carries fake entries with a count of zero.
            if (candidate.equalsIgnoreCase(suffix) && !line.endsWith(":0")) {
                return true;
            }
        }
        return false;
    }

    private static String sha1(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required by the breach corpus format", e);
        }
    }
}
