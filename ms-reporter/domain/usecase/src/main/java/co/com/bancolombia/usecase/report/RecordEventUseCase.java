package co.com.bancolombia.usecase.report;
import co.com.bancolombia.model.report.DomainEvent;
import co.com.bancolombia.model.report.gateways.EventStore;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class RecordEventUseCase {
    private final EventStore eventStore;
    public Mono<DomainEvent> execute(DomainEvent event) {
        return eventStore.append(event);
    }
}
