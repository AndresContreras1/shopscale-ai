package co.gamestore.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Writes the link to the application log until the email provider is wired up.
 *
 * <p>The token is a credential, so this is only acceptable while the log is not shipped anywhere and
 * the store is not yet open. The notifications module replaces it.
 */
@Component
public class LoggingAccountMessenger implements AccountMessenger {

    private static final Logger log = LoggerFactory.getLogger(LoggingAccountMessenger.class);

    private final String baseUrl;

    public LoggingAccountMessenger(@Value("${app.base-url:http://localhost:8088}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public void sendEmailVerification(User user, String token) {
        log.info("Email verification for {}: {}/verify-email?token={}", user.getEmail(), baseUrl, token);
    }

    @Override
    public void sendPasswordReset(User user, String token) {
        log.info("Password reset for {}: {}/reset-password?token={}", user.getEmail(), baseUrl, token);
    }
}
