package co.com.bancolombia.model.cart;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @Builder(toBuilder=true) @AllArgsConstructor @NoArgsConstructor
public class CartItem {
    private String productId;
    private String productName;
    private int quantity;
    private BigDecimal unitPrice;

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
