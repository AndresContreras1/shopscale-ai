package co.gamestore.audit;

import co.gamestore.common.CurrentActor;
import co.gamestore.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    /** Joins the caller's transaction: if the business change rolls back, its audit entry does too. */
    @Transactional
    public void record(String action, String entityType, Object entityId, String details) {
        record(CurrentActor.name(), action, entityType, entityId, details);
    }

    @Transactional
    public void record(String actor, String action, String entityType, Object entityId, String details) {
        repository.save(new AuditLog(actor, action, entityType, entityId == null ? null : entityId.toString(),
                truncate(details)));
    }

    /** Security events (e.g. failed logins) must be kept even when the surrounding request fails. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(String actor, String action, String details) {
        repository.save(new AuditLog(actor, action, "User", null, truncate(details)));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> latest(Pageable pageable) {
        return PageResponse.from(repository.findAllByOrderByCreatedAtDesc(pageable).map(AuditLogResponse::from));
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
