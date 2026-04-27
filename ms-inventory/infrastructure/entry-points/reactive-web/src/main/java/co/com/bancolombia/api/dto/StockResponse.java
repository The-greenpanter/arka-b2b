package co.com.bancolombia.api.dto;

import co.com.bancolombia.model.stock.Stock;
import co.com.bancolombia.model.stock.StockStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO de salida para operaciones REST en Stock.
 *
 * Expone el estado actual del stock de manera legible por el cliente HTTP.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StockResponse {

    @JsonProperty("stockId")
    private String stockId;

    @JsonProperty("productId")
    private String productId;

    @JsonProperty("availableQty")
    private Integer availableQty;

    @JsonProperty("reservedQty")
    private Integer reservedQty;

    @JsonProperty("totalQty")
    private Integer totalQty;

    @JsonProperty("status")
    private String status;

    @JsonProperty("minimumThreshold")
    private Integer minimumThreshold;

    @JsonProperty("alertSent")
    private Boolean alertSent;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("updatedAt")
    private Instant updatedAt;

    public static StockResponse fromDomain(Stock stock) {
        return StockResponse.builder()
                .stockId(stock.getStockId())
                .productId(stock.getProductId())
                .availableQty(stock.getAvailableQty())
                .reservedQty(stock.getReservedQty())
                .totalQty(stock.getAvailableQty() + stock.getReservedQty())
                .status(stock.getStatus().name())
                .minimumThreshold(stock.getMinimumThreshold())
                .alertSent(stock.getAlertSent())
                .createdAt(stock.getCreatedAt())
                .updatedAt(stock.getUpdatedAt())
                .build();
    }
}
