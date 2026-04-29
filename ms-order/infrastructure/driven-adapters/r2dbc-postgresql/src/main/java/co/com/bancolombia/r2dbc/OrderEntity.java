package co.com.bancolombia.r2dbc;

import co.com.bancolombia.model.order.Order;
import co.com.bancolombia.model.order.OrderStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
@Table("orders")
public class OrderEntity implements Persistable<String> {
    @Id private String orderId;
    @Transient @Builder.Default private boolean isNew = true;
    private String cartId;
    private String customerId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private Instant createdAt;
    private Instant updatedAt;

    @Override
    public String getId() { return orderId; }

    public Order toDomain() {
        return Order.builder()
                .orderId(orderId).cartId(cartId).customerId(customerId)
                .status(status).totalAmount(totalAmount)
                .createdAt(createdAt).updatedAt(updatedAt)
                .build();
    }

    public static OrderEntity fromDomain(Order o) {
        return OrderEntity.builder()
                .orderId(o.getOrderId()).cartId(o.getCartId()).customerId(o.getCustomerId())
                .status(o.getStatus()).totalAmount(o.getTotalAmount())
                .createdAt(o.getCreatedAt()).updatedAt(o.getUpdatedAt())
                .build();
    }
}
