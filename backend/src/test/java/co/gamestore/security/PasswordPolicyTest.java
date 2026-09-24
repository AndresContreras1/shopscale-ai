package co.gamestore.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.gamestore.common.BrandProperties;
import co.gamestore.common.ProblemException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private static final BrandProperties BRAND = new BrandProperties(
            "Game Store", "gamestore.co", "hola@gamestore.co", "datos@gamestore.co",
            "https://gamestore.co/problems");

    private final Set<String> breached = Set.of("correct horse battery staple");
    private final PasswordPolicy policy = new PasswordPolicy(breached::contains, BRAND);

    @Test
    void rejectsSomethingTooShortToBeWorthGuessing() {
        assertThatThrownBy(() -> policy.validate("short1234"))
                .isInstanceOf(ProblemException.class)
                .hasMessageContaining("at least 12");
    }

    @Test
    void rejectsAnythingBcryptWouldSilentlyTruncate() {
        assertThatThrownBy(() -> policy.validate("a".repeat(73)))
                .isInstanceOf(ProblemException.class)
                .hasMessageContaining("at most 72");
    }

    @Test
    void rejectsAPasswordBuiltFromTheStoreName() {
        assertThatThrownBy(() -> policy.validate("my gamestore password"))
                .isInstanceOf(ProblemException.class)
                .hasMessageContaining("name of the store");
    }

    @Test
    void seesThroughAccentsAndCapitalsInTheBlockedWords() {
        assertThatThrownBy(() -> policy.validate("MiCónsolaFavorita"))
                .isInstanceOf(ProblemException.class)
                .hasMessageContaining("name of the store");
    }

    @Test
    void rejectsAPasswordThatIsAlreadyInABreach() {
        assertThatThrownBy(() -> policy.validate("correct horse battery staple"))
                .isInstanceOf(ProblemException.class)
                .hasMessageContaining("known data breach");
    }

    /**
     * The point of the NIST rules: a long, ordinary phrase passes, and no rule demands a symbol or a
     * digit. This password would be rejected by the composition rules it replaces.
     */
    @Test
    void acceptsALongOrdinaryPhraseWithNoSymbolsAtAll() {
        assertThatCode(() -> policy.validate("una tarde tranquila de sabado")).doesNotThrowAnyException();
    }
}
