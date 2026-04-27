package co.com.bancolombia.mongo;

import co.com.bancolombia.model.cart.Cart;
import co.com.bancolombia.model.cart.CartItem;
import co.com.bancolombia.model.cart.CartStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Document(collection = "carts")
public class CartDocument {
    @Id private String cartId;
    @Indexed private String customerId;
    private CartStatus status;
    private List<CartItemDoc> items;
    private Instant createdAt;
    private Instant updatedAt;

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CartItemDoc {
        private String productId;
        private String productName;
        private int quantity;
        private BigDecimal unitPrice;
    }

    public Cart toDomain() {
        return Cart.builder()
                .cartId(cartId)
                .customerId(customerId)
                .status(status)
                .items(items == null ? new java.util.ArrayList<>() : items.stream()
                        .map(i -> CartItem.builder()
                                .productId(i.getProductId())
                                .productName(i.getProductName())
                                .quantity(i.getQuantity())
                                .unitPrice(i.getUnitPrice())
                                .build())
                        .collect(Collectors.toList()))
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static CartDocument fromDomain(Cart c) {
        return CartDocument.builder()
                .cartId(c.getCartId())
                .customerId(c.getCustomerId())
                .status(c.getStatus())
                .items(c.getItems() == null ? List.of() : c.getItems().stream()
                        .map(i -> CartItemDoc.builder()
                                .productId(i.getProductId())
                                .productName(i.getProductName())
                                .quantity(i.getQuantity())
                                .unitPrice(i.getUnitPrice())
                                .build())
                        .collect(Collectors.toList()))
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
