package co.com.bancolombia.mongo;

import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.ProductStatus;
import co.com.bancolombia.model.product.Supplier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Modelo de PERSISTENCIA. Vive en el adapter, no en el dominio.
 *
 * Esto es importante: si Mongo cambia su esquema o anotaciones, el dominio
 * no se entera. La barrera entre dominio y persistencia se cruza con
 * mappers explícitos (toDomain / fromDomain).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "products")
public class ProductDocument {

    @Id
    private String productId;

    @Indexed
    private String name;

    private String description;

    @Indexed
    private String category;

    @Indexed
    private ProductStatus status;

    private BigDecimal basePriceUsd;
    private BigDecimal taxRate;
    private Integer minOrderQty;
    private Integer maxOrderQty;
    private Supplier supplier;
    private Map<String, Object> attributes;
    private Instant createdAt;
    private Instant updatedAt;

    public Product toDomain() {
        return Product.builder()
                .productId(productId)
                .name(name)
                .description(description)
                .category(category)
                .status(status)
                .basePriceUsd(basePriceUsd)
                .taxRate(taxRate)
                .minOrderQty(minOrderQty)
                .maxOrderQty(maxOrderQty)
                .supplier(supplier)
                .attributes(attributes)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static ProductDocument fromDomain(Product p) {
        return ProductDocument.builder()
                .productId(p.getProductId())
                .name(p.getName())
                .description(p.getDescription())
                .category(p.getCategory())
                .status(p.getStatus())
                .basePriceUsd(p.getBasePriceUsd())
                .taxRate(p.getTaxRate())
                .minOrderQty(p.getMinOrderQty())
                .maxOrderQty(p.getMaxOrderQty())
                .supplier(p.getSupplier())
                .attributes(p.getAttributes())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
