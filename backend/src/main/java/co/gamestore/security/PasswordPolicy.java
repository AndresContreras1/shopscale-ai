package co.gamestore.security;

import co.gamestore.common.ProblemException;
import co.gamestore.common.ProblemType;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * What counts as an acceptable password, following NIST 800-63B-4.
 *
 * <p>The rules that are absent matter as much as the ones that are here. There is no requirement for
 * an uppercase letter, a digit or a symbol, and no forced expiry. Those rules were removed from the
 * guideline because they push people towards predictable shapes such as "Password1!" and towards
 * writing the result down. Length and a check against known breaches do the work instead.
 */
@Component
@RequiredArgsConstructor
public class PasswordPolicy {

    /** The guideline requires at least 8 and recommends 15; 12 is where usability and strength meet. */
    public static final int MIN_LENGTH = 12;

    /** BCrypt silently ignores anything past 72 bytes, so a longer password is a false promise. */
    public static final int MAX_LENGTH = 72;

    /** Words from this store's own context, which a breach corpus does not know are weak here. */
    private static final Set<String> CONTEXT_WORDS =
            Set.of("gamestore", "game store", "consola", "console", "playstation", "xbox", "nintendo");

    private final BreachedPasswordChecker breachedPasswords;

    /**
     * @throws ProblemException with a message the user can act on, and nothing else. The message
     *     never says whether the account exists, only what is wrong with the password.
     */
    public void validate(String password) {
        // Evaluated in order and stopped at the first complaint, so a password that is too short
        // never travels to the breach service. Each check returns null when it is satisfied.
        Stream.<Supplier<String>>of(
                        () -> lengthProblem(password),
                        () -> contextProblem(password),
                        () -> breachProblem(password))
                .map(Supplier::get)
                .filter(problem -> problem != null)
                .findFirst()
                .ifPresent(problem -> {
                    throw new ProblemException(ProblemType.PASSWORD_REJECTED, problem);
                });
    }

    private static String lengthProblem(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "The password must be at least " + MIN_LENGTH + " characters long";
        }
        if (password.length() > MAX_LENGTH) {
            return "The password must be at most " + MAX_LENGTH + " characters long";
        }
        return null;
    }

    private static String contextProblem(String password) {
        String normalized = Normalizer.normalize(password, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        boolean namesTheStore = CONTEXT_WORDS.stream().anyMatch(normalized::contains);
        return namesTheStore ? "The password cannot be built from the name of the store or its products" : null;
    }

    private String breachProblem(String password) {
        return breachedPasswords.isBreached(password)
                ? "This password appears in a known data breach, so it is already being guessed"
                : null;
    }
}
