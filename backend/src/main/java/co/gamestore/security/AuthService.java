package co.gamestore.security;

import co.gamestore.audit.AuditService;
import co.gamestore.common.BusinessException;
import co.gamestore.common.NotFoundException;
import co.gamestore.security.AuthDtos.LoginRequest;
import co.gamestore.security.AuthDtos.RegisterRequest;
import co.gamestore.security.AuthDtos.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
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
        return user;
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
