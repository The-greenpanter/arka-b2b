package co.com.bancolombia.usecase.cart;

import co.com.bancolombia.model.cart.*;
import co.com.bancolombia.model.cart.gateways.CartRepository;
import co.com.bancolombia.model.cart.gateways.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutUseCaseTest {

    @Mock private CartRepository repository;
    @Mock private EventPublisher eventPublisher;
    private CheckoutUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CheckoutUseCase(repository, eventPublisher);
        when(eventPublisher.publish(anyString(), anyString(), any())).thenReturn(Mono.empty());
    }

    @Test
    void shouldCheckoutCartAndEmitEvent() {
        Cart cart = Cart.builder()
                .cartId("cart-001")
                .customerId("cust-001")
                .status(CartStatus.OPEN)
                .items(new ArrayList<>(List.of(
                        CartItem.builder().productId("p1").quantity(2).unitPrice(BigDecimal.TEN).productName("P1").build()
                )))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(repository.findById("cart-001")).thenReturn(Mono.just(cart));
        when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute("cart-001"))
                .assertNext(c -> assertEquals(CartStatus.CHECKED_OUT, c.getStatus()))
                .verifyComplete();

        verify(eventPublisher).publish(eq("cart.checkout-requested"), eq("cart-001"), any());
    }

    @Test
    void shouldErrorWhenCartNotFound() {
        when(repository.findById("x")).thenReturn(Mono.empty());
        StepVerifier.create(useCase.execute("x"))
                .expectError(IllegalArgumentException.class)
                .verify();
    }
}
