package co.com.bancolombia.model.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Value Object embebido en Product.
 *
 * En DDD un Value Object no tiene identidad propia: dos Suppliers con los
 * mismos campos son iguales. Por simplicidad lo guardamos como subdocumento
 * dentro del Product (en MongoDB es natural; en SQL sería tabla aparte).
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class Supplier {
    private String supplierId;
    private String name;
    private Integer leadTimeDays;
}
