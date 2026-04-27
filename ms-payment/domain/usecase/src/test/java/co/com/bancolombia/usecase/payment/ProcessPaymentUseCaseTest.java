package co.com.bancolombia.usecase.payment;
import co.com.bancolombia.model.payment.*;
import co.com.bancolombia.model.payment.gateways.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessPaymentUseCaseTest {
    @Mock private PaymentRepository repository;
    @Mock private EventPublisher eventPublisher;
    private ProcessPaymentUseCase useCase;

    @BeforeEach void setUp() {
        useCase = new ProcessPaymentUseCase(repository, eventPublisher);
        when(eventPublisher.publish(anyString(), anyString(), any())).thenReturn(Mono.empty());
        when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @Test void shouldProcessPaymentAndEmitEvent() {
        StepVerifier.create(useCase.execute("ord-001", BigDecimal.TEN))
                .assertNext(p -> {
                    assertEquals(PaymentStatus.PROCESSED, p.getStatus());
                    assertEquals("ord-001", p.getOrderId());
                })
                .verifyComplete();
        verify(eventPublisher).publish(eq("payment.payment-processed"), eq("ord-001"), any());
    }
}
