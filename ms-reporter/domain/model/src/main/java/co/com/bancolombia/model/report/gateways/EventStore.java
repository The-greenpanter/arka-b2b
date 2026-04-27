package co.com.bancolombia.model.report.gateways;
import co.com.bancolombia.model.report.DomainEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
public interface EventStore {
    Mono<DomainEvent> append(DomainEvent event);
    Flux<DomainEvent> findByEventType(String eventType);
    Flux<DomainEvent> findByAggregateId(String aggregateId);
}
