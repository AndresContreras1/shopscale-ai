package co.gamestore.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The consent ledger.
 *
 * <p>Decreto 1377 asks the company to be able to prove consent, not merely to claim it. So each
 * decision is stored with the moment, the channel, the version of the policy that was shown and a
 * hash of the address it came from. Revoking is a new entry, which means the history survives.
 */
@Service
@RequiredArgsConstructor
public class ConsentService {

    private final ConsentRepository consents;
    private final UserRepository users;

    @Value("${app.legal.privacy-policy-version:2026-09}")
    private String policyVersion;

    @Transactional
    public void record(User user, ConsentPurpose purpose, boolean granted, String source, String ip,
                       String userAgent) {
        consents.save(new Consent(user, purpose, granted, policyVersion, source, hash(ip), trim(userAgent)));
    }

    /** What the person agreed to right now: the newest entry per purpose wins. */
    @Transactional(readOnly = true)
    public Map<ConsentPurpose, Boolean> current(Long userId) {
        // Nothing is assumed: a purpose with no entry at all counts as refused.
        Map<ConsentPurpose, Boolean> latest = new LinkedHashMap<>();
        Arrays.stream(ConsentPurpose.values()).forEach(purpose -> latest.put(purpose, false));

        Set<ConsentPurpose> decided = EnumSet.noneOf(ConsentPurpose.class);
        // Newest first, so the first entry seen for a purpose is the decision that stands.
        for (Consent entry : consents.findByUserIdOrderByCreatedAtDesc(userId)) {
            if (decided.add(entry.getPurpose())) {
                latest.put(entry.getPurpose(), entry.isGranted());
            }
        }
        return latest;
    }

    @Transactional
    public Map<ConsentPurpose, Boolean> update(String email, Map<ConsentPurpose, Boolean> decisions,
                                               String ip, String userAgent) {
        User user = users.findByEmailIgnoreCase(email).orElseThrow();
        decisions.forEach((purpose, granted) -> record(user, purpose, granted, "account-settings", ip, userAgent));
        return current(user.getId());
    }

    /** The ledger as it goes into a data export: every decision, in order. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(Long userId) {
        return consents.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(entry -> Map.<String, Object>of(
                        "purpose", entry.getPurpose().name(),
                        "granted", entry.isGranted(),
                        "policyVersion", entry.getPolicyVersion(),
                        "source", entry.getSource(),
                        "recordedAt", entry.getCreatedAt().toString()))
                .toList();
    }

    private static String trim(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;
    }

    private static String hash(String ip) {
        if (ip == null) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(ip.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
