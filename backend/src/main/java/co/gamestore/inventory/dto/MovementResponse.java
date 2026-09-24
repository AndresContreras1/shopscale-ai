package co.gamestore.inventory.dto;

import co.gamestore.inventory.MovementType;
import co.gamestore.inventory.StockMovement;
import java.time.Instant;

public record MovementResponse(Long id, MovementType type, int quantity, int onHandAfter, int reservedAfter,
                               String reason, String reference, String createdBy, Instant createdAt) {

    public static MovementResponse from(StockMovement m) {
        return new MovementResponse(m.getId(), m.getType(), m.getQuantity(), m.getOnHandAfter(),
                m.getReservedAfter(), m.getReason(), m.getReference(), m.getCreatedBy(), m.getCreatedAt());
    }
}
