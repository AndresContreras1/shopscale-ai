package co.gamestore.security;

import co.gamestore.audit.AuditService;
import co.gamestore.common.BusinessException;
import co.gamestore.common.NotFoundException;
import co.gamestore.security.AuthDtos.AuthResponse;
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
    private final JwtService jwtService;
    private final AuditService auditService;

    /** Compared against when the email does not exist, so both cases take the same time (no user enumeration). */
    private String dummyHash;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email()).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash();
        boolean valid = passwordEncoder.matches(request.password(), hash);
        if (user == null || !valid || !user.isEnabled()) {
            auditService.recordIndependently(request.email(), "LOGIN_FAILED", "Invalid credentials");
            throw new BadCredentialsException("Invalid credentials");
        }
        auditService.record(user.getEmail(), "LOGIN", "User", user.getId(), null);
        return toAuthResponse(user);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BusinessException("Email already registered");
        }
        User user = userRepository.save(new User(request.email().toLowerCase(),
                passwordEncoder.encode(request.password()), request.fullName(), Role.CUSTOMER));
        auditService.record(user.getEmail(), "REGISTER", "User", user.getId(), null);
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me(String email) {
        return userRepository.findByEmailIgnoreCase(email).map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User", email));
    }

    private AuthResponse toAuthResponse(User user) {
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthResponse(token.token(), token.expiresAt(), UserResponse.from(user));
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("dummy-password-for-timing");
        }
        return dummyHash;
    }
}
