package co.com.bancolombia.usecase.report;
import co.com.bancolombia.model.report.DomainEvent;
import co.com.bancolombia.model.report.gateways.EventStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordEventUseCaseTest {
    @Mock EventStore eventStore;

    @Test void shouldRecordEvent() {
        DomainEvent evt = DomainEvent.builder().eventId("e-1").eventType("test")
                .aggregateId("agg-1").payload("{}").occurredAt(Instant.now()).build();
        when(eventStore.append(any())).thenReturn(Mono.just(evt));
        RecordEventUseCase useCase = new RecordEventUseCase(eventStore);
        StepVerifier.create(useCase.execute(evt))
                .assertNext(e -> assertEquals("test", e.getEventType()))
                .verifyComplete();
    }
}
