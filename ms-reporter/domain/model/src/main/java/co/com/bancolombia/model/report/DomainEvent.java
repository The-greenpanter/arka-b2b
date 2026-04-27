package co.com.bancolombia.model.report;
import lombok.*;
import java.time.Instant;

@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class DomainEvent {
    private String eventId;
    private String eventType;
    private String aggregateId;
    private String payload;
    private Instant occurredAt;
}
