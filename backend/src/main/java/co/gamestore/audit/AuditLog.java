package co.gamestore.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Who changed what and when: price changes, stock corrections, logins. Append-only.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "ix_audit_created", columnList = "created_at"),
        @Index(name = "ix_audit_entity", columnList = "entity_type, entity_id")
})
@Getter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String actor;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(length = 60)
    private String entityType;

    @Column(length = 60)
    private String entityId;

    @Column(length = 1000)
    private String details;

    @Column(nullable = false)
    private Instant createdAt;

    public AuditLog(String actor, String action, String entityType, String entityId, String details) {
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.createdAt = Instant.now();
    }
}
