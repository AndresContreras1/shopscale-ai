package co.gamestore.security;

import co.gamestore.audit.AuditService;
import co.gamestore.common.BusinessException;
import co.gamestore.common.NotFoundException;
import co.gamestore.common.ProblemException;
import co.gamestore.common.ProblemType;
import co.gamestore.security.AuthDtos.LoginRequest;
import co.gamestore.security.AuthDtos.RegisterRequest;
import co.gamestore.security.AuthDtos.UserResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final PasswordPolicy passwordPolicy;
    private final LoginAttemptGuard loginAttempts;
    private final AuthTokenService authTokens;
    private final AccountMessenger messenger;
    private final FindByIndexNameSessionRepository<?> sessions;

    private static final Duration VERIFICATION_VALIDITY = Duration.ofHours(24);

    /** Short on purpose: a reset link that lives in an inbox for a week is a spare key. */
    private static final Duration RESET_VALIDITY = Duration.ofMinutes(15);

    /** Compared against when the email does not exist, so both cases take the same time (no user enumeration). */
    private String dummyHash;

    /**
     * Checks the credentials and returns the user. Issuing the session is the controller's job: this
     * service decides who you are, not how the browser remembers it.
     */
    @Transactional
    public User authenticate(LoginRequest request) {
        // Checked before the password, so an account under attack stays shut even if the attacker
        // finally guesses right. The answer is the same either way: no hint that the account exists,
        // no hint that it is being throttled.
        if (loginAttempts.isBlocked(request.email())) {
            auditService.recordIndependently(request.email(), "LOGIN_BLOCKED", "Too many failed attempts");
            throw new BadCredentialsException("Invalid credentials");
        }

        User user = userRepository.findByEmailIgnoreCase(request.email()).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash();
        boolean valid = passwordEncoder.matches(request.password(), hash);
        if (user == null || !valid || !user.isEnabled()) {
            loginAttempts.recordFailure(request.email());
            auditService.recordIndependently(request.email(), "LOGIN_FAILED", "Invalid credentials");
            throw new BadCredentialsException("Invalid credentials");
        }
        auditService.record(user.getEmail(), "LOGIN", "User", user.getId(), null);
        return user;
    }

    @Transactional
    public User register(RegisterRequest request) {
        passwordPolicy.validate(request.password());
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BusinessException("Email already registered");
        }
        User user = userRepository.save(new User(request.email().toLowerCase(),
                passwordEncoder.encode(request.password()), request.fullName(), Role.CUSTOMER));
        auditService.record(user.getEmail(), "REGISTER", "User", user.getId(), null);
        messenger.sendEmailVerification(user, authTokens.issue(user, AuthTokenPurpose.EMAIL_VERIFICATION,
                VERIFICATION_VALIDITY));
        return user;
    }

    /** Confirms ownership of the address. A wrong, expired or already used token looks the same. */
    @Transactional
    public void verifyEmail(String token) {
        User user = authTokens.redeem(token, AuthTokenPurpose.EMAIL_VERIFICATION)
                .orElseThrow(() -> new ProblemException(ProblemType.TOKEN_INVALID,
                        "This link is no longer valid. Ask for a new one."));
        user.markEmailVerified(Instant.now());
        auditService.record(user.getEmail(), "EMAIL_VERIFIED", "User", user.getId(), null);
    }

    /**
     * Sends a fresh link if the address belongs to an account. The caller is never told whether it
     * does: answering differently would turn this endpoint into a list of registered customers.
     */
    @Transactional
    public void requestEmailVerification(String email) {
        userRepository.findByEmailIgnoreCase(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> messenger.sendEmailVerification(user,
                        authTokens.issue(user, AuthTokenPurpose.EMAIL_VERIFICATION, VERIFICATION_VALIDITY)));
    }

    /** Same silence as above, for the same reason. */
    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmailIgnoreCase(email)
                .filter(User::isEnabled)
                .ifPresent(user -> {
                    messenger.sendPasswordReset(user,
                            authTokens.issue(user, AuthTokenPurpose.PASSWORD_RESET, RESET_VALIDITY));
                    auditService.recordIndependently(user.getEmail(), "PASSWORD_RESET_REQUESTED", null);
                });
    }

    /**
     * Sets a new password and signs the account out everywhere. If the reset was triggered because
     * somebody else had the old password, leaving their session alive would defeat the whole exercise.
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = authTokens.redeem(token, AuthTokenPurpose.PASSWORD_RESET)
                .orElseThrow(() -> new ProblemException(ProblemType.TOKEN_INVALID,
                        "This link is no longer valid. Ask for a new one."));
        passwordPolicy.validate(newPassword);
        user.changePassword(passwordEncoder.encode(newPassword));
        // Proving control of the inbox also proves the address, so a reset verifies it.
        if (!user.isEmailVerified()) {
            user.markEmailVerified(Instant.now());
        }
        signOutEverywhere(user.getEmail());
        auditService.record(user.getEmail(), "PASSWORD_RESET", "User", user.getId(), null);
    }

    private void signOutEverywhere(String email) {
        Map<String, ? extends Session> active = sessions.findByPrincipalName(email);
        active.keySet().forEach(sessions::deleteById);
    }

    @Transactional(readOnly = true)
    public User requireById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User", String.valueOf(id)));
    }

    @Transactional(readOnly = true)
    public User requireByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow(() -> new NotFoundException("User", email));
    }

    @Transactional(readOnly = true)
    public UserResponse me(String email) {
        return userRepository.findByEmailIgnoreCase(email).map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User", email));
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("dummy-password-for-timing");
        }
        return dummyHash;
    }
}
