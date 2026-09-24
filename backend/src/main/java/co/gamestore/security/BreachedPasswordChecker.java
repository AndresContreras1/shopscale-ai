package co.gamestore.security;

/**
 * Tells whether a password is already known to attackers.
 *
 * <p>NIST 800-63B-4 replaced composition rules with this check: a password of eight characters that
 * nobody has ever seen is safer than "P@ssw0rd1", which satisfies every rule and is in every list.
 */
public interface BreachedPasswordChecker {

    boolean isBreached(String password);
}
