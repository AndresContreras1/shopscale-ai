package co.gamestore.security;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByTokenHashAndPurpose(String tokenHash, AuthTokenPurpose purpose);

    /** Asking for a new link retires the previous ones, so only the newest message works. */
    @Modifying
    @Query("update AuthToken t set t.usedAt = :now where t.user.id = :userId and t.purpose = :purpose "
            + "and t.usedAt is null")
    int retireOutstanding(@Param("userId") Long userId, @Param("purpose") AuthTokenPurpose purpose,
                          @Param("now") Instant now);

    @Modifying
    @Query("delete from AuthToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
