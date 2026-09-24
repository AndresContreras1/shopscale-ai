package co.gamestore.inventory;

import co.gamestore.common.BusinessException;
import co.gamestore.common.ConflictRetry;
import co.gamestore.inventory.dto.FlashSaleRequest;
import co.gamestore.inventory.dto.FlashSaleResult;
import co.gamestore.inventory.dto.InventoryResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Demo of the overselling problem: many buyers try to reserve the same product at the same instant.
 * Reservations are released at the end, so the simulation leaves the real stock untouched
 * (the ledger keeps the trace under the reference {@value #REFERENCE}).
 */
@Service
@RequiredArgsConstructor
public class FlashSaleSimulator {

    static final String REFERENCE = "FLASH-SALE-SIM";
    private static final int THREADS = 16;

    private final InventoryService inventoryService;
    private final ConflictRetry conflictRetry;

    public FlashSaleResult run(FlashSaleRequest request) {
        Long productId = request.productId();
        int units = request.unitsPerBuyer();
        InventoryResponse before = inventoryService.find(productId);

        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Boolean>> attempts = new ArrayList<>();
        long start = System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            for (int i = 0; i < request.buyers(); i++) {
                attempts.add(pool.submit(() -> {
                    startGate.await();
                    try {
                        conflictRetry.run(() -> inventoryService.reserve(productId, units, REFERENCE));
                        return true;
                    } catch (BusinessException noStock) {
                        return false;
                    }
                }));
            }
            startGate.countDown();
            int ok = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get()) {
                    ok++;
                }
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            InventoryResponse after = inventoryService.find(productId);
            int reserved = ok * units;
            if (reserved > 0) {
                conflictRetry.run(() -> inventoryService.release(productId, reserved, REFERENCE));
            }
            return new FlashSaleResult(before.sku(), request.buyers(), units, before.available(), ok,
                    request.buyers() - ok, reserved, after.available(), reserved > before.available(), elapsedMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Simulation interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("Simulation failed: " + e.getCause().getMessage(), e.getCause());
        } finally {
            pool.shutdown();
        }
    }
}
