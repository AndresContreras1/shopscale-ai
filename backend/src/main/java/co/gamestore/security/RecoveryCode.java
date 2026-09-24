package co.gamestore.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A single-use way back in when the phone with the authenticator is lost, stolen or reset. */
@Entity
@Table(name = "recovery_codes")
@Getter
@NoArgsConstructor
public class RecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 64, unique = true)
    private String codeHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant usedAt;

    RecoveryCode(User user, String codeHash) {
        this.user = user;
        this.codeHash = codeHash;
        this.createdAt = Instant.now();
    }

    void markUsed(Instant when) {
        this.usedAt = when;
    }
}
