package co.gamestore.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Used by tests and by any environment without outbound internet access. */
@Component
@ConditionalOnProperty(name = "app.security.password.breach-check", havingValue = "false")
public class BreachCheckDisabled implements BreachedPasswordChecker {

    @Override
    public boolean isBreached(String password) {
        return false;
    }
}
