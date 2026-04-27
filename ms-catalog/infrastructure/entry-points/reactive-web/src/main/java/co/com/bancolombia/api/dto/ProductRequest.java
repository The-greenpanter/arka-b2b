package co.com.bancolombia.api.dto;

import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.Supplier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

/**
 * DTO de entrada del endpoint POST /api/products.
 *
 * Vive en la capa entry-point porque es un detalle del transporte HTTP.
 * El dominio NO conoce este DTO. La conversión a Product se hace aquí
 * (Anti-Corruption Layer simétrico al de salida).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {

    private String name;
    private String description;
    private String category;
    private BigDecimal basePriceUsd;
    private BigDecimal taxRate;
    private Integer minOrderQty;
    private Integer maxOrderQty;
    private SupplierDto supplier;
    private Map<String, Object> attributes;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplierDto {
        private String supplierId;
        private String name;
        private Integer leadTimeDays;
    }

    public Product toDomain() {
        Supplier domainSupplier = supplier == null ? null : Supplier.builder()
                .supplierId(supplier.getSupplierId())
                .name(supplier.getName())
                .leadTimeDays(supplier.getLeadTimeDays())
                .build();

        return Product.builder()
                .name(name)
                .description(description)
                .category(category)
                .basePriceUsd(basePriceUsd)
                .taxRate(taxRate)
                .minOrderQty(minOrderQty)
                .maxOrderQty(maxOrderQty)
                .supplier(domainSupplier)
                .attributes(attributes)
                .build();
    }
}
