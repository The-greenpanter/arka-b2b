package co.com.bancolombia.usecase.product;

import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.ProductStatus;
import co.com.bancolombia.model.product.gateways.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmProductUseCaseTest {

    @Mock
    private ProductRepository repository;

    private ConfirmProductUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ConfirmProductUseCase(repository);
    }

    @Test
    @DisplayName("Confirma producto: EN_CREACION_STOCK → CONFIRMADO (saga paso 3)")
    void shouldConfirmProductFromEnCreacionStock() {
        Product enCreacionStock = Product.builder()
                .productId("prod-001")
                .name("Monitor")
                .status(ProductStatus.EN_CREACION_STOCK)
                .basePriceUsd(BigDecimal.valueOf(499))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(repository.findById("prod-001")).thenReturn(Mono.just(enCreacionStock));
        when(repository.save(any(Product.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute("prod-001"))
                .assertNext(confirmed -> {
                    assertEquals(ProductStatus.CONFIRMADO, confirmed.getStatus());
                    assertEquals("prod-001", confirmed.getProductId());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Error si el producto no existe")
    void shouldErrorWhenProductNotFound() {
        when(repository.findById("no-existe")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("no-existe"))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    @DisplayName("Error si el producto no está en EN_CREACION_STOCK (transición inválida)")
    void shouldErrorWhenNotInCorrectState() {
        Product wrongState = Product.builder()
                .productId("prod-002")
                .name("Teclado")
                .status(ProductStatus.VALIDANDO_PROVEEDOR)
                .basePriceUsd(BigDecimal.TEN)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(repository.findById("prod-002")).thenReturn(Mono.just(wrongState));

        StepVerifier.create(useCase.execute("prod-002"))
                .expectError(IllegalStateException.class)
                .verify();
    }
}
