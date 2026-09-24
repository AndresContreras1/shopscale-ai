package co.gamestore.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 120) String email,
            // Length and blocklist rules live in PasswordPolicy, in one place, with one message.
            @NotBlank String password,
            @NotBlank @Size(max = 120) String fullName) {
    }

    public record UserResponse(Long id, String email, String fullName, Role role, boolean emailVerified) {

        static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                    user.isEmailVerified());
        }
    }

    public record EmailRequest(@NotBlank @Email String email) {
    }

    public record TokenRequest(@NotBlank String token) {
    }

    public record PasswordResetRequest(@NotBlank String token, @NotBlank String password) {
    }

    public record CodeRequest(@NotBlank String code) {
    }

    /**
     * What the sign-in produced. Either the session is open and the user is here, or the account holds
     * a second factor and the browser must come back with a code.
     */
    public record LoginResult(boolean mfaRequired, boolean mfaEnrolmentRequired, UserResponse user) {

        static LoginResult pendingSecondFactor() {
            return new LoginResult(true, false, null);
        }

        static LoginResult signedIn(User user) {
            return new LoginResult(false, user.requiresSecondFactor() && !user.isTotpEnabled(),
                    UserResponse.from(user));
        }
    }

    public record MfaEnrolmentResponse(String secret, String provisioningUri) {
    }

    public record RecoveryCodesResponse(java.util.List<String> recoveryCodes) {
    }
}
