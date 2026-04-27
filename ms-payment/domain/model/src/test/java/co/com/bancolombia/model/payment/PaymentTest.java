package co.com.bancolombia.model.payment;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class PaymentTest {
    @Test void shouldMarkProcessed() {
        Payment p = Payment.builder().paymentId("pay-001").orderId("ord-001")
                .amount(BigDecimal.TEN).status(PaymentStatus.PENDING).createdAt(Instant.now()).build();
        p.markProcessed();
        assertEquals(PaymentStatus.PROCESSED, p.getStatus());
        assertNotNull(p.getProcessedAt());
    }
    @Test void shouldMarkFailed() {
        Payment p = Payment.builder().paymentId("pay-002").orderId("ord-002")
                .amount(BigDecimal.TEN).status(PaymentStatus.PENDING).createdAt(Instant.now()).build();
        p.markFailed("Insufficient funds");
        assertEquals(PaymentStatus.FAILED, p.getStatus());
        assertEquals("Insufficient funds", p.getFailureReason());
    }
}
