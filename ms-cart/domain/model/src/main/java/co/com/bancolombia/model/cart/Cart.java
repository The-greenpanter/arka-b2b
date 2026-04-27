package co.com.bancolombia.model.cart;

import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @Builder(toBuilder=true) @AllArgsConstructor @NoArgsConstructor
public class Cart {
    private String cartId;
    private String customerId;
    private CartStatus status;
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;

    public Cart addItem(CartItem item) {
        if (this.status != CartStatus.OPEN) throw new IllegalStateException("Cart is not open");
        List<CartItem> newItems = new ArrayList<>(this.items);
        newItems.removeIf(i -> i.getProductId().equals(item.getProductId()));
        newItems.add(item);
        this.items = newItems;
        this.updatedAt = Instant.now();
        return this;
    }

    public Cart removeItem(String productId) {
        if (this.status != CartStatus.OPEN) throw new IllegalStateException("Cart is not open");
        this.items = new ArrayList<>(this.items);
        this.items.removeIf(i -> i.getProductId().equals(productId));
        this.updatedAt = Instant.now();
        return this;
    }

    public Cart checkout() {
        if (this.status != CartStatus.OPEN) throw new IllegalStateException("Cart is not open");
        if (this.items.isEmpty()) throw new IllegalStateException("Cart is empty");
        this.status = CartStatus.CHECKED_OUT;
        this.updatedAt = Instant.now();
        return this;
    }

    public BigDecimal total() {
        return items.stream().map(CartItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
