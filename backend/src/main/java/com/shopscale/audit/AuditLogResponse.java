package com.shopscale.audit;

import java.time.Instant;

public record AuditLogResponse(Long id, String actor, String action, String entityType, String entityId,
                               String details, Instant createdAt) {

    static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActor(), log.getAction(), log.getEntityType(),
                log.getEntityId(), log.getDetails(), log.getCreatedAt());
    }
}
