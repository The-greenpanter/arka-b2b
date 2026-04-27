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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetProductUseCaseTest {

    @Mock
    private ProductRepository repository;

    private GetProductUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetProductUseCase(repository);
    }

    @Test
    @DisplayName("Retorna producto existente por ID")
    void shouldReturnProductWhenExists() {
        Product expected = Product.builder()
                .productId("prod-001")
                .name("Teclado RGB")
                .status(ProductStatus.CONFIRMADO)
                .basePriceUsd(BigDecimal.valueOf(79.99))
                .build();

        when(repository.findById("prod-001")).thenReturn(Mono.just(expected));

        StepVerifier.create(useCase.execute("prod-001"))
                .assertNext(p -> {
                    assertEquals("prod-001", p.getProductId());
                    assertEquals("Teclado RGB", p.getName());
                    assertEquals(ProductStatus.CONFIRMADO, p.getStatus());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Lanza IllegalArgumentException si el producto no existe")
    void shouldErrorWhenProductNotFound() {
        when(repository.findById("inexistente")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("inexistente"))
                .expectErrorMatches(e ->
                        e instanceof IllegalArgumentException
                                && e.getMessage().contains("inexistente"))
                .verify();
    }
}
