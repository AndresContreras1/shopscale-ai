package co.gamestore.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.gamestore.catalog.ProductRepository;
import co.gamestore.common.BusinessException;
import co.gamestore.common.ConflictRetry;
import co.gamestore.inventory.dto.FlashSaleRequest;
import co.gamestore.inventory.dto.FlashSaleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class InventoryConcurrencyTest {

    @Autowired
    InventoryService inventoryService;

    @Autowired
    FlashSaleSimulator simulator;

    @Autowired
    ConflictRetry conflictRetry;

    @Autowired
    ProductRepository productRepository;

    Long productId;

    @BeforeEach
    void setUp() {
        productId = productRepository.findBySku("SPRT-OUT-001").orElseThrow().getId();
    }

    @Test
    void flashSaleNeverOversells() {
        int available = inventoryService.find(productId).available();

        FlashSaleResult result = simulator.run(new FlashSaleRequest(productId, 200, 1));

        assertThat(result.successfulReservations()).isEqualTo(Math.min(200, available));
        assertThat(result.oversold()).isFalse();
        assertThat(result.minAvailableObserved()).isGreaterThanOrEqualTo(0);
        // The simulation releases its reservations: real stock is unchanged.
        assertThat(inventoryService.find(productId).available()).isEqualTo(available);
    }

    @Test
    void reserveCommitAndReleaseKeepTheLedgerConsistent() {
        InventoryItemSnapshot before = snapshot();

        conflictRetry.run(() -> inventoryService.reserve(productId, 2, "TEST-1"));
        assertThat(snapshot().reserved()).isEqualTo(before.reserved() + 2);

        conflictRetry.run(() -> inventoryService.commit(productId, 2, "TEST-1"));
        InventoryItemSnapshot afterSale = snapshot();
        assertThat(afterSale.onHand()).isEqualTo(before.onHand() - 2);
        assertThat(afterSale.reserved()).isEqualTo(before.reserved());

        conflictRetry.run(() -> inventoryService.receive(productId, 2, "Restock", "PO-1"));
        assertThat(snapshot().onHand()).isEqualTo(before.onHand());
    }

    @Test
    void rejectsReservationBeyondAvailableStock() {
        int available = inventoryService.find(productId).available();
        assertThatThrownBy(() -> conflictRetry.run(() -> inventoryService.reserve(productId, available + 1, "TEST-2")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Not enough stock");
    }

    private InventoryItemSnapshot snapshot() {
        var r = inventoryService.find(productId);
        return new InventoryItemSnapshot(r.onHand(), r.reserved());
    }

    private record InventoryItemSnapshot(int onHand, int reserved) {
    }
}
