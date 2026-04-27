package co.com.bancolombia.api.dto;

import co.com.bancolombia.model.stock.Stock;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * DTO de entrada para operaciones REST en Stock.
 *
 * Casos de uso:
 *  - POST /api/stocks: productId
 *  - POST /api/stocks/{productId}/reserve: quantity
 *  - POST /api/stocks/{productId}/restock: quantity
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StockRequest {

    @JsonProperty("productId")
    private String productId;

    @JsonProperty("providerId")
    private String providerId;

    @JsonProperty("quantity")
    private Integer quantity;

    @JsonProperty("minimumThreshold")
    private Integer minimumThreshold;

    public Stock toDomain() {
        return Stock.builder()
                .productId(this.productId)
                .providerId(this.providerId)
                .availableQty(0)
                .reservedQty(0)
                .minimumThreshold(this.minimumThreshold != null ? this.minimumThreshold : 10)
                .alertSent(false)
                .build();
    }
}
