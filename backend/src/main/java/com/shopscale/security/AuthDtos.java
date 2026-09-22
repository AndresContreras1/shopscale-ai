package com.shopscale.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Size(min = 8, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain letters and digits")
            String password,
            @NotBlank @Size(max = 120) String fullName) {
    }

    public record UserResponse(Long id, String email, String fullName, Role role) {

        static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
        }
    }

    public record AuthResponse(String token, Instant expiresAt, UserResponse user) {
    }
}
