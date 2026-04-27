package co.com.bancolombia.model.cart;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class CartTest {

    private Cart openCart() {
        return Cart.builder()
                .cartId("cart-001")
                .customerId("cust-001")
                .status(CartStatus.OPEN)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private CartItem item(String productId, int qty, double price) {
        return CartItem.builder()
                .productId(productId)
                .productName("Product " + productId)
                .quantity(qty)
                .unitPrice(BigDecimal.valueOf(price))
                .build();
    }

    @Test
    void shouldAddItemToCart() {
        Cart cart = openCart().addItem(item("prod-1", 2, 10.0));
        assertEquals(1, cart.getItems().size());
        assertEquals(BigDecimal.valueOf(20.0), cart.total());
    }

    @Test
    void shouldReplaceItemWithSameProductId() {
        Cart cart = openCart()
                .addItem(item("prod-1", 2, 10.0))
                .addItem(item("prod-1", 5, 10.0));
        assertEquals(1, cart.getItems().size());
        assertEquals(5, cart.getItems().get(0).getQuantity());
    }

    @Test
    void shouldRemoveItem() {
        Cart cart = openCart()
                .addItem(item("prod-1", 1, 10.0))
                .addItem(item("prod-2", 2, 5.0))
                .removeItem("prod-1");
        assertEquals(1, cart.getItems().size());
        assertEquals("prod-2", cart.getItems().get(0).getProductId());
    }

    @Test
    void shouldCheckout() {
        Cart cart = openCart()
                .addItem(item("prod-1", 1, 100.0))
                .checkout();
        assertEquals(CartStatus.CHECKED_OUT, cart.getStatus());
    }

    @Test
    void shouldThrowWhenCheckoutOnEmptyCart() {
        assertThrows(IllegalStateException.class, () -> openCart().checkout());
    }

    @Test
    void shouldThrowWhenAddingToCheckedOutCart() {
        Cart cart = openCart().addItem(item("prod-1", 1, 10.0)).checkout();
        assertThrows(IllegalStateException.class, () -> cart.addItem(item("prod-2", 1, 5.0)));
    }
}
