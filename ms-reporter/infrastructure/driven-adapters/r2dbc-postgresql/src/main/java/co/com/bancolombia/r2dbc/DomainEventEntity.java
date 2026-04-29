package co.com.bancolombia.r2dbc;

import co.com.bancolombia.model.report.DomainEvent;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Table("domain_events")
public class DomainEventEntity implements Persistable<String> {

    @Id
    @Column("event_id")
    private String eventId;
    @Transient @Builder.Default private boolean isNew = true;

    @Column("event_type")
    private String eventType;

    @Column("aggregate_id")
    private String aggregateId;

    @Column("payload")
    private String payload;

    @Column("occurred_at")
    private Instant occurredAt;

    @Override
    public String getId() { return eventId; }

    public static DomainEventEntity fromDomain(DomainEvent event) {
        return DomainEventEntity.builder()
                .eventId(event.getEventId())
                .eventType(event.getEventType())
                .aggregateId(event.getAggregateId())
                .payload(event.getPayload())
                .occurredAt(event.getOccurredAt())
                .build();
    }

    public DomainEvent toDomain() {
        return DomainEvent.builder()
                .eventId(this.eventId)
                .eventType(this.eventType)
                .aggregateId(this.aggregateId)
                .payload(this.payload)
                .occurredAt(this.occurredAt)
                .build();
    }
}
