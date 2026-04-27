package co.com.bancolombia.model.stock;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/** Evento emitido cuando el stock cae por debajo del umbral mínimo (HU3). */
@Getter
@Builder
public class StockLowAlertEvent {
    private String productId;
    private String providerId;
    private Integer currentStock;
    private Integer minimumThreshold;
    private String triggerReason;  // ORDER_RESERVATION | MANUAL_ADJUSTMENT
    private Instant timestamp;
}
