package co.gamestore.security;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {

    Optional<RecoveryCode> findByCodeHashAndUsedAtIsNull(String codeHash);

    void deleteByUserId(Long userId);

    long countByUserIdAndUsedAtIsNull(Long userId);
}
