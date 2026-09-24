package co.gamestore.security;

import co.gamestore.audit.AuditService;
import co.gamestore.common.ProblemException;
import co.gamestore.common.ProblemType;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enrolment and verification of the staff second factor.
 *
 * <p>A password that leaks is enough to reach the back office, where prices, stock and customer data
 * live. A second factor makes the password alone useless, and the accounts that hold that power are
 * the ones that get it.
 */
@Service
@RequiredArgsConstructor
public class MfaService {

    private static final int RECOVERY_CODE_COUNT = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RecoveryCodeRepository recoveryCodes;
    private final AuditService audit;
    private final SecretEncryptor secrets;

    @Value("${app.security.mfa.issuer:Game Store}")
    private String issuer;

    /**
     * Starts enrolment. The secret is returned once, for the authenticator app, and the factor is not
     * active until a code proves the app actually holds it.
     */
    @Transactional
    public Enrolment startEnrolment(String email) {
        User user = users.findByEmailIgnoreCase(email).orElseThrow();
        String secret = TimeBasedOneTimePassword.newSecret();
        user.startTotpEnrolment(secrets.encrypt(secret));
        return new Enrolment(secret, TimeBasedOneTimePassword.provisioningUri(issuer, user.getEmail(), secret));
    }

    /**
     * Turns the factor on once a code from the app matches, and hands over the recovery codes. They
     * are shown this once: what is stored is their hash, so nobody can read them back out later.
     */
    @Transactional
    public List<String> activate(String email, String code) {
        User user = users.findByEmailIgnoreCase(email).orElseThrow();
        if (user.getTotpSecret() == null) {
            throw new ProblemException(ProblemType.BAD_REQUEST, "Start the enrolment first");
        }
        if (!TimeBasedOneTimePassword.matches(secrets.decrypt(user.getTotpSecret()), code, Instant.now())) {
            throw new ProblemException(ProblemType.MFA_REQUIRED, "That code does not match");
        }
        user.enableTotp(Instant.now());
        audit.record(user.getEmail(), "MFA_ENABLED", "User", user.getId(), null);
        return issueRecoveryCodes(user);
    }

    /** Accepts either a code from the app or one of the recovery codes, which then stops working. */
    @Transactional
    public boolean verify(User user, String code) {
        if (user.getTotpSecret() != null
                && TimeBasedOneTimePassword.matches(secrets.decrypt(user.getTotpSecret()), code, Instant.now())) {
            return true;
        }
        return redeemRecoveryCode(user, code);
    }

    @Transactional
    public void disable(String email) {
        User user = users.findByEmailIgnoreCase(email).orElseThrow();
        user.disableTotp();
        recoveryCodes.deleteByUserId(user.getId());
        audit.record(user.getEmail(), "MFA_DISABLED", "User", user.getId(), null);
    }

    public long remainingRecoveryCodes(Long userId) {
        return recoveryCodes.countByUserIdAndUsedAtIsNull(userId);
    }

    private boolean redeemRecoveryCode(User user, String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT).replace("-", "");
        return recoveryCodes.findByCodeHashAndUsedAtIsNull(AuthTokenService.hash(normalized))
                .filter(found -> found.getUser().getId().equals(user.getId()))
                .map(found -> {
                    found.markUsed(Instant.now());
                    audit.recordIndependently(user.getEmail(), "MFA_RECOVERY_CODE_USED", null);
                    return true;
                })
                .orElse(false);
    }

    private List<String> issueRecoveryCodes(User user) {
        recoveryCodes.deleteByUserId(user.getId());
        List<String> plain = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String code = randomCode();
            plain.add(code.substring(0, 5) + "-" + code.substring(5));
            recoveryCodes.save(new RecoveryCode(user, AuthTokenService.hash(code)));
        }
        return plain;
    }

    private static String randomCode() {
        // No vowels and no look-alike characters: these get read out loud and typed from paper.
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            code.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return code.toString();
    }

    public record Enrolment(String secret, String provisioningUri) {
    }
}
