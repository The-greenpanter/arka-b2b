package co.com.bancolombia.model.order;

import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter @Setter @Builder(toBuilder=true) @AllArgsConstructor @NoArgsConstructor
public class Order {
    private String orderId;
    private String cartId;
    private String customerId;
    private OrderStatus status;
    private List<OrderItem> items;
    private BigDecimal totalAmount;
    private Instant createdAt;
    private Instant updatedAt;

    public Order process() {
        if (this.status != OrderStatus.PENDING)
            throw new IllegalStateException("Order must be PENDING to process. Current: " + this.status);
        this.status = OrderStatus.PROCESSING;
        this.updatedAt = Instant.now();
        return this;
    }

    public Order complete() {
        if (this.status != OrderStatus.PROCESSING)
            throw new IllegalStateException("Order must be PROCESSING to complete. Current: " + this.status);
        this.status = OrderStatus.COMPLETED;
        this.updatedAt = Instant.now();
        return this;
    }

    public Order cancel() {
        if (this.status == OrderStatus.COMPLETED)
            throw new IllegalStateException("Cannot cancel COMPLETED order");
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = Instant.now();
        return this;
    }
}
