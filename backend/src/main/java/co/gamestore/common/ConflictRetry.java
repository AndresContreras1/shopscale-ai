package co.gamestore.common;

import jakarta.persistence.OptimisticLockException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs an operation in its own transaction and retries it when another request updated the same rows
 * first (optimistic locking conflict). Each retry re-reads fresh data, so business rules such as
 * "enough stock available" are evaluated again instead of being bypassed.
 */
@Component
public class ConflictRetry {

    private static final Logger log = LoggerFactory.getLogger(ConflictRetry.class);
    private static final int MAX_ATTEMPTS = 5;

    private final TransactionTemplate tx;

    public ConflictRetry(PlatformTransactionManager transactionManager) {
        this.tx = new TransactionTemplate(transactionManager);
    }

    public <T> T execute(Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return tx.execute(status -> action.get());
            } catch (OptimisticLockingFailureException | OptimisticLockException ex) {
                if (attempt == MAX_ATTEMPTS) {
                    throw ex;
                }
                log.debug("Concurrent update detected, retrying (attempt {})", attempt + 1);
                backoff(attempt);
            }
        }
    }

    public void run(Runnable action) {
        execute(() -> {
            action.run();
            return null;
        });
    }

    private static void backoff(int attempt) {
        try {
            Thread.sleep((long) (10 * attempt + Math.random() * 20));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
