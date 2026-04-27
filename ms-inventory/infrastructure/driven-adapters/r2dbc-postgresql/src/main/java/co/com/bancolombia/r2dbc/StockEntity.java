package co.com.bancolombia.r2dbc;

import co.com.bancolombia.model.stock.Stock;
import co.com.bancolombia.model.stock.StockStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Entity de R2DBC/PostgreSQL para el agregado Stock.
 *
 * Mapea las columnas de la tabla 'stock' a los atributos del dominio.
 * Nota: NO contiene reglas de negocio, solo datos.
 */
@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Table("stock")
public class StockEntity {

    @Id
    @Column("stock_id")
    private String stockId;

    @Column("product_id")
    private String productId;

    @Column("available_qty")
    private Integer availableQty;

    @Column("reserved_qty")
    private Integer reservedQty;

    @Column("status")
    private String status;

    @Column("provider_id")
    private String providerId;

    @Column("minimum_threshold")
    private Integer minimumThreshold;

    @Column("alert_sent")
    private Boolean alertSent;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    public static StockEntity fromDomain(Stock stock) {
        return StockEntity.builder()
                .stockId(stock.getStockId())
                .productId(stock.getProductId())
                .providerId(stock.getProviderId())
                .availableQty(stock.getAvailableQty())
                .reservedQty(stock.getReservedQty())
                .status(stock.getStatus() != null ? stock.getStatus().name() : StockStatus.ACTIVE.name())
                .minimumThreshold(stock.getMinimumThreshold() != null ? stock.getMinimumThreshold() : 10)
                .alertSent(stock.getAlertSent() != null ? stock.getAlertSent() : false)
                .createdAt(stock.getCreatedAt())
                .updatedAt(stock.getUpdatedAt())
                .build();
    }

    public Stock toDomain() {
        return Stock.builder()
                .stockId(this.stockId)
                .productId(this.productId)
                .providerId(this.providerId)
                .availableQty(this.availableQty)
                .reservedQty(this.reservedQty)
                .status(StockStatus.valueOf(this.status))
                .minimumThreshold(this.minimumThreshold != null ? this.minimumThreshold : 10)
                .alertSent(this.alertSent != null ? this.alertSent : false)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt)
                .build();
    }
}
