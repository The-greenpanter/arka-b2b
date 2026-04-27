package co.com.bancolombia.usecase.report;
import co.com.bancolombia.model.report.DomainEvent;
import co.com.bancolombia.model.report.gateways.EventStore;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class GetEventsUseCase {
    private final EventStore eventStore;
    public Flux<DomainEvent> byType(String eventType) {
        return eventStore.findByEventType(eventType);
    }
    public Flux<DomainEvent> byAggregate(String aggregateId) {
        return eventStore.findByAggregateId(aggregateId);
    }
}
