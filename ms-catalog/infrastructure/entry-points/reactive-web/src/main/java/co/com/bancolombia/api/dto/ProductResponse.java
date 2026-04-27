package co.com.bancolombia.api.dto;

import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private String productId;
    private String name;
    private String description;
    private String category;
    private ProductStatus status;
    private BigDecimal basePriceUsd;
    private BigDecimal taxRate;
    private Integer minOrderQty;
    private Integer maxOrderQty;
    private ProductRequest.SupplierDto supplier;
    private Map<String, Object> attributes;
    private Instant createdAt;
    private Instant updatedAt;

    public static ProductResponse fromDomain(Product p) {
        ProductRequest.SupplierDto sup = p.getSupplier() == null ? null
                : ProductRequest.SupplierDto.builder()
                    .supplierId(p.getSupplier().getSupplierId())
                    .name(p.getSupplier().getName())
                    .leadTimeDays(p.getSupplier().getLeadTimeDays())
                    .build();

        return ProductResponse.builder()
                .productId(p.getProductId())
                .name(p.getName())
                .description(p.getDescription())
                .category(p.getCategory())
                .status(p.getStatus())
                .basePriceUsd(p.getBasePriceUsd())
                .taxRate(p.getTaxRate())
                .minOrderQty(p.getMinOrderQty())
                .maxOrderQty(p.getMaxOrderQty())
                .supplier(sup)
                .attributes(p.getAttributes())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
