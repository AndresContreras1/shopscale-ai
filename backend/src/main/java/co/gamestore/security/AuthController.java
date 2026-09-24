package co.gamestore.security;

import co.gamestore.common.ProblemException;
import co.gamestore.common.ProblemType;
import co.gamestore.security.AuthDtos.CodeRequest;
import co.gamestore.security.AuthDtos.EmailRequest;
import co.gamestore.security.AuthDtos.LoginRequest;
import co.gamestore.security.AuthDtos.LoginResult;
import co.gamestore.security.AuthDtos.MfaEnrolmentResponse;
import co.gamestore.security.AuthDtos.PasswordResetRequest;
import co.gamestore.security.AuthDtos.RecoveryCodesResponse;
import co.gamestore.security.AuthDtos.RegisterRequest;
import co.gamestore.security.AuthDtos.TokenRequest;
import co.gamestore.security.AuthDtos.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign in, register and identity. Logging out is handled by the filter chain at the same path, so the
 * session is invalidated by Spring Security itself.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /** Marks a browser that proved the password but still owes a code. It is not an authentication. */
    private static final String PENDING_SECOND_FACTOR = "co.gamestore.mfa.pending";

    private final AuthService authService;
    private final MfaService mfaService;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    @PostMapping("/login")
    public LoginResult login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
                             HttpServletResponse httpResponse) {
        User user = authService.authenticate(request);
        if (user.isTotpEnabled()) {
            // The password was right, and that is all it buys. No authentication is stored yet.
            HttpSession session = renewSession(httpRequest);
            session.setAttribute(PENDING_SECOND_FACTOR, user.getId());
            return LoginResult.pendingSecondFactor();
        }
        startSession(user, httpRequest, httpResponse);
        return LoginResult.signedIn(user);
    }

    /** Second step of a staff sign-in: a code from the authenticator app, or a recovery code. */
    @PostMapping("/mfa/verify")
    public LoginResult verifySecondFactor(@Valid @RequestBody CodeRequest request, HttpServletRequest httpRequest,
                                          HttpServletResponse httpResponse) {
        HttpSession session = httpRequest.getSession(false);
        Object pending = session == null ? null : session.getAttribute(PENDING_SECOND_FACTOR);
        if (pending == null) {
            throw new ProblemException(ProblemType.MFA_REQUIRED, "Start again from the sign-in page");
        }
        User user = authService.requireById((Long) pending);
        if (!mfaService.verify(user, request.code())) {
            throw new ProblemException(ProblemType.MFA_REQUIRED, "That code does not match");
        }
        session.removeAttribute(PENDING_SECOND_FACTOR);
        startSession(user, httpRequest, httpResponse);
        return LoginResult.signedIn(user);
    }

    /** Hands over the secret for the authenticator app. The factor stays off until a code proves it. */
    @PostMapping("/mfa/enrol")
    public MfaEnrolmentResponse startEnrolment(Principal principal) {
        var enrolment = mfaService.startEnrolment(principal.getName());
        return new MfaEnrolmentResponse(enrolment.secret(), enrolment.provisioningUri());
    }

    /** Turns the factor on and returns the recovery codes, which are shown this once and never again. */
    @PostMapping("/mfa/activate")
    public RecoveryCodesResponse activate(@Valid @RequestBody CodeRequest request, Principal principal) {
        return new RecoveryCodesResponse(mfaService.activate(principal.getName(), request.code()));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResult register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest,
                                HttpServletResponse httpResponse) {
        User user = authService.register(request);
        startSession(user, httpRequest, httpResponse);
        return LoginResult.signedIn(user);
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody TokenRequest request) {
        authService.verifyEmail(request.token());
    }

    /**
     * Always accepted, whether or not the address belongs to an account. Answering differently would
     * turn this into a way of checking who shops here.
     */
    @PostMapping("/verify-email/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendVerification(@Valid @RequestBody EmailRequest request) {
        authService.requestEmailVerification(request.email());
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody EmailRequest request) {
        authService.requestPasswordReset(request.email());
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request.token(), request.password());
    }

    @GetMapping("/me")
    public UserResponse me(Principal principal) {
        return authService.me(principal.getName());
    }

    /**
     * Any session the visitor arrived with is thrown away and a new one is created, so an attacker who
     * planted a session id before the login cannot reuse it afterwards (session fixation). Nothing of
     * value is lost: the cart lives in the browser and the CSRF token in its own cookie.
     */
    private UserResponse startSession(User user, HttpServletRequest request, HttpServletResponse response) {
        renewSession(request);

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);

        return UserResponse.from(user);
    }

    private static HttpSession renewSession(HttpServletRequest request) {
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        return request.getSession(true);
    }
}
