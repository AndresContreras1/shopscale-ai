package com.shopscale.inventory;

import com.shopscale.common.ConflictRetry;
import com.shopscale.common.PageResponse;
import com.shopscale.inventory.dto.FlashSaleRequest;
import com.shopscale.inventory.dto.FlashSaleResult;
import com.shopscale.inventory.dto.InventoryResponse;
import com.shopscale.inventory.dto.MovementResponse;
import com.shopscale.inventory.dto.ReorderPointRequest;
import com.shopscale.inventory.dto.StockChangeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final FlashSaleSimulator flashSaleSimulator;
    private final ConflictRetry conflictRetry;

    @GetMapping
    public PageResponse<InventoryResponse> list(
            @RequestParam(defaultValue = "false") boolean lowStock,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("product.sku"));
        return inventoryService.list(lowStock, pageable);
    }

    @GetMapping("/{productId}")
    public InventoryResponse find(@PathVariable Long productId) {
        return inventoryService.find(productId);
    }

    @GetMapping("/{productId}/movements")
    public PageResponse<MovementResponse> movements(@PathVariable Long productId,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return inventoryService.movements(productId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    /** Goods received from a supplier. */
    @PostMapping("/{productId}/receipts")
    public InventoryResponse receive(@PathVariable Long productId, @Valid @RequestBody StockChangeRequest request) {
        return conflictRetry.execute(() ->
                inventoryService.receive(productId, request.quantity(), request.reason(), request.reference()));
    }

    /** Manual correction after a physical count; negative quantities remove units. */
    @PostMapping("/{productId}/adjustments")
    public InventoryResponse adjust(@PathVariable Long productId, @Valid @RequestBody StockChangeRequest request) {
        return conflictRetry.execute(() ->
                inventoryService.adjust(productId, request.quantity(), request.reason(), request.reference()));
    }

    @PutMapping("/{productId}/reorder-point")
    public InventoryResponse updateReorderPoint(@PathVariable Long productId,
                                                @Valid @RequestBody ReorderPointRequest request) {
        return conflictRetry.execute(() -> inventoryService.updateReorderPoint(productId, request.reorderPoint()));
    }

    /** Fires concurrent reservations against one product to prove stock never goes negative. */
    @PostMapping("/simulations/flash-sale")
    public FlashSaleResult flashSale(@Valid @RequestBody FlashSaleRequest request) {
        return flashSaleSimulator.run(request);
    }
}
