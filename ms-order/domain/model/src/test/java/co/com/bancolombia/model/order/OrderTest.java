package co.com.bancolombia.model.order;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OrderTest {
    private Order pendingOrder() {
        return Order.builder()
                .orderId("ord-001").cartId("cart-001").customerId("cust-001")
                .status(OrderStatus.PENDING)
                .items(List.of(OrderItem.builder().productId("p1").quantity(1).unitPrice(BigDecimal.TEN).productName("P1").build()))
                .totalAmount(BigDecimal.TEN).createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }

    @Test void shouldProcessOrder() {
        Order o = pendingOrder().process();
        assertEquals(OrderStatus.PROCESSING, o.getStatus());
    }

    @Test void shouldCompleteOrder() {
        Order o = pendingOrder().process().complete();
        assertEquals(OrderStatus.COMPLETED, o.getStatus());
    }

    @Test void shouldCancelPendingOrder() {
        Order o = pendingOrder().cancel();
        assertEquals(OrderStatus.CANCELLED, o.getStatus());
    }

    @Test void shouldThrowWhenProcessingCompletedOrder() {
        assertThrows(IllegalStateException.class, () -> pendingOrder().process().complete().process());
    }

    @Test void shouldThrowWhenCancellingCompletedOrder() {
        assertThrows(IllegalStateException.class, () -> pendingOrder().process().complete().cancel());
    }
}
