package co.gamestore.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    /** BCrypt hash; the plain password is never stored. */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Proof that the address belongs to whoever registered it, not just that it was typed correctly. */
    @Column(nullable = false)
    private boolean emailVerified;

    private Instant emailVerifiedAt;

    /** Base32 shared secret for the authenticator app; present from enrolment, active once proved. */
    @Column(length = 255)
    private String totpSecret;

    @Column(nullable = false)
    private boolean totpEnabled;

    private Instant totpEnrolledAt;

    /** Set when the person exercised their right to deletion; the row stays, the person does not. */
    private Instant anonymizedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public User(String email, String passwordHash, String fullName, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
    }

    /**
     * For accounts an operator creates rather than someone registering: the address was not typed by
     * a stranger, so there is nothing to prove by sending a link to it.
     */
    public static User createdByStaff(String email, String passwordHash, String fullName, Role role) {
        User user = new User(email, passwordHash, fullName, role);
        user.emailVerified = true;
        user.emailVerifiedAt = Instant.now();
        return user;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    void markEmailVerified(Instant when) {
        this.emailVerified = true;
        this.emailVerifiedAt = when;
    }

    void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    void startTotpEnrolment(String secret) {
        this.totpSecret = secret;
        this.totpEnabled = false;
    }

    void enableTotp(Instant when) {
        this.totpEnabled = true;
        this.totpEnrolledAt = when;
    }

    void disableTotp() {
        this.totpSecret = null;
        this.totpEnabled = false;
        this.totpEnrolledAt = null;
    }

    void anonymize(String pseudonym, Instant when) {
        this.email = pseudonym;
        this.fullName = "Deleted account";
        this.passwordHash = "{noop}";
        this.enabled = false;
        this.emailVerified = false;
        this.totpSecret = null;
        this.totpEnabled = false;
        this.anonymizedAt = when;
    }

    /** Staff reach prices, stock and customer data, so their accounts carry the second factor. */
    public boolean requiresSecondFactor() {
        return role == Role.ADMIN || role == Role.OPERATOR;
    }
}
