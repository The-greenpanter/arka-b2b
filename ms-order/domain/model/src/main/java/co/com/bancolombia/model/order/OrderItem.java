package co.com.bancolombia.model.order;
import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class OrderItem {
    private String productId;
    private String productName;
    private int quantity;
    private BigDecimal unitPrice;
}
