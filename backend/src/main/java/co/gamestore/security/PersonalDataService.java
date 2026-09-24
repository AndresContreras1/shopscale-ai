package co.gamestore.security;

import co.gamestore.audit.AuditService;
import co.gamestore.common.PersonalDataContributor;
import co.gamestore.common.PublicId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two rights that need code rather than a policy document: getting a copy, and being forgotten.
 *
 * <p>Ley 1581 gives a person the right to know what is held, to correct it, and to have it deleted.
 * Deletion is not always literal: an invoice has to be kept for the tax authority. What can go is the
 * link between the record and the person, which is what anonymisation does here.
 */
@Service
@RequiredArgsConstructor
public class PersonalDataService {

    private final UserRepository users;
    private final ConsentService consents;
    private final AuditService audit;
    private final FindByIndexNameSessionRepository<?> sessions;
    private final List<PersonalDataContributor> contributors;

    @Transactional(readOnly = true)
    public Map<String, Object> export(String email) {
        User user = users.findByEmailIgnoreCase(email).orElseThrow();
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("generatedAt", Instant.now().toString());
        export.put("profile", Map.of(
                "email", user.getEmail(),
                "fullName", user.getFullName(),
                "role", user.getRole().name(),
                "emailVerified", user.isEmailVerified(),
                "registeredAt", user.getCreatedAt().toString()));
        export.put("consents", consents.history(user.getId()));
        contributors.forEach(contributor -> export.put(contributor.section(), contributor.export(email)));
        // The export itself is a read, so the audit entry is written in its own transaction.
        audit.recordIndependently(user.getEmail(), "DATA_EXPORTED", null);
        return export;
    }

    /**
     * Replaces the person with a pseudonym everywhere, keeps the records the law requires and ends
     * every session. The account can no longer be signed into, and nothing points back to a human.
     */
    @Transactional
    public void anonymize(String email) {
        User user = users.findByEmailIgnoreCase(email).orElseThrow();
        String pseudonym = "anon-" + PublicId.next() + "@deleted.invalid";

        contributors.forEach(contributor -> contributor.anonymize(user.getEmail(), pseudonym));
        audit.record(user.getEmail(), "ACCOUNT_ANONYMIZED", "User", user.getId(), null);
        sessions.findByPrincipalName(user.getEmail()).keySet().forEach(sessions::deleteById);
        user.anonymize(pseudonym, Instant.now());
    }
}
