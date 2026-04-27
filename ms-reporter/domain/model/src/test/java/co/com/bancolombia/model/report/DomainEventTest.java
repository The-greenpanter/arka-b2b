package co.com.bancolombia.model.report;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class DomainEventTest {
    @Test void shouldBuildEvent() {
        DomainEvent e = DomainEvent.builder()
                .eventId("evt-001").eventType("order.order-created")
                .aggregateId("ord-001").payload("{}").occurredAt(Instant.now()).build();
        assertEquals("order.order-created", e.getEventType());
    }
}
