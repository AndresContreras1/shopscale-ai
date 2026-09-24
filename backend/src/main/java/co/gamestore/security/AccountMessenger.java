package co.gamestore.security;

/**
 * Delivers the links that prove an address or reset a password.
 *
 * <p>A port, not an implementation: the transactional email provider arrives with the notifications
 * module. Until then the link is written to the log, which is enough to operate the store by hand and
 * keeps the rest of this feature testable today.
 */
public interface AccountMessenger {

    void sendEmailVerification(User user, String token);

    void sendPasswordReset(User user, String token);
}
