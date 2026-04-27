package co.com.bancolombia.usecase.order;

import co.com.bancolombia.model.order.*;
import co.com.bancolombia.model.order.gateways.EventPublisher;
import co.com.bancolombia.model.order.gateways.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateOrderUseCaseTest {
    @Mock private OrderRepository repository;
    @Mock private EventPublisher eventPublisher;
    private CreateOrderUseCase useCase;

    @BeforeEach void setUp() {
        useCase = new CreateOrderUseCase(repository, eventPublisher);
        when(eventPublisher.publish(anyString(), anyString(), any())).thenReturn(Mono.empty());
    }

    @Test void shouldCreateOrderAndEmitEvent() {
        Order input = Order.builder()
                .orderId("ord-001").cartId("cart-001").customerId("cust-001")
                .items(List.of(OrderItem.builder().productId("p1").quantity(1)
                        .unitPrice(BigDecimal.TEN).productName("P1").build()))
                .totalAmount(BigDecimal.TEN).createdAt(Instant.now()).updatedAt(Instant.now())
                .build();

        when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(input))
                .assertNext(o -> assertEquals(OrderStatus.PENDING, o.getStatus()))
                .verifyComplete();

        verify(eventPublisher).publish(eq("order.order-created"), eq("ord-001"), any());
    }
}
