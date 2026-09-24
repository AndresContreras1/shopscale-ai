package co.gamestore.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** One entry in the consent ledger. Rows are never updated: a change of mind is a new row. */
@Entity
@Table(name = "consents")
@Getter
@NoArgsConstructor
public class Consent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ConsentPurpose purpose;

    @Column(nullable = false)
    private boolean granted;

    @Column(nullable = false, length = 20)
    private String policyVersion;

    @Column(nullable = false, length = 40)
    private String source;

    @Column(length = 64)
    private String ipHash;

    @Column(length = 255)
    private String userAgent;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    Consent(User user, ConsentPurpose purpose, boolean granted, String policyVersion, String source,
            String ipHash, String userAgent) {
        this.user = user;
        this.purpose = purpose;
        this.granted = granted;
        this.policyVersion = policyVersion;
        this.source = source;
        this.ipHash = ipHash;
        this.userAgent = userAgent;
        this.createdAt = Instant.now();
    }
}
