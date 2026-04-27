package co.com.bancolombia.r2dbc;

import co.com.bancolombia.model.report.DomainEvent;
import co.com.bancolombia.model.report.gateways.EventStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class EventStoreR2dbcAdapter implements EventStore {

    private final DomainEventR2dbcRepository r2dbcRepository;

    @Override
    public Mono<DomainEvent> append(DomainEvent event) {
        return r2dbcRepository.save(DomainEventEntity.fromDomain(event))
                .map(DomainEventEntity::toDomain);
    }

    @Override
    public Flux<DomainEvent> findByEventType(String eventType) {
        return r2dbcRepository.findByEventType(eventType)
                .map(DomainEventEntity::toDomain);
    }

    @Override
    public Flux<DomainEvent> findByAggregateId(String aggregateId) {
        return r2dbcRepository.findByAggregateId(aggregateId)
                .map(DomainEventEntity::toDomain);
    }
}
