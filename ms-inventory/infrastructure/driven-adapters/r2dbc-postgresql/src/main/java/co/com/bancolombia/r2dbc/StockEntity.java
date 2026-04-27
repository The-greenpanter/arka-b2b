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

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    /**
     * Mapea dominio → entidad (para guardar).
     */
    public static StockEntity fromDomain(Stock stock) {
        return StockEntity.builder()
                .stockId(stock.getStockId())
                .productId(stock.getProductId())
                .availableQty(stock.getAvailableQty())
                .reservedQty(stock.getReservedQty())
                .status(stock.getStatus().name())
                .createdAt(stock.getCreatedAt())
                .updatedAt(stock.getUpdatedAt())
                .build();
    }

    /**
     * Mapea entidad → dominio (para recuperar).
     */
    public Stock toDomain() {
        return Stock.builder()
                .stockId(this.stockId)
                .productId(this.productId)
                .availableQty(this.availableQty)
                .reservedQty(this.reservedQty)
                .status(StockStatus.valueOf(this.status))
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt)
                .build();
    }
}
