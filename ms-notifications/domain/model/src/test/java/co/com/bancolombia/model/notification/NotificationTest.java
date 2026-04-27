package co.com.bancolombia.model.notification;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class NotificationTest {
    @Test void shouldBuildNotification() {
        Notification n = Notification.builder()
                .notificationId("n-001").type(NotificationType.ORDER_CREATED)
                .recipientId("cust-001").message("Your order was created").sentAt(Instant.now())
                .build();
        assertEquals(NotificationType.ORDER_CREATED, n.getType());
        assertEquals("cust-001", n.getRecipientId());
    }
}
