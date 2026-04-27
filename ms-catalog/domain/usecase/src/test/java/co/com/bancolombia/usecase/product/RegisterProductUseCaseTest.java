package co.com.bancolombia.usecase.product;

import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.ProductStatus;
import co.com.bancolombia.model.product.Supplier;
import co.com.bancolombia.model.product.gateways.EventPublisher;
import co.com.bancolombia.model.product.gateways.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegisterProductUseCaseTest {

    @Mock
    private ProductRepository repository;

    @Mock
    private EventPublisher eventPublisher;

    private RegisterProductUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegisterProductUseCase(repository, eventPublisher);
        when(eventPublisher.publish(anyString(), anyString(), any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Registra producto → VALIDANDO_PROVEEDOR y emite catalog.product-validated")
    void shouldRegisterProductAndEmitEvent() {
        when(repository.save(any(Product.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        Product input = Product.builder()
                .name("Monitor 4K")
                .category("MONITORES")
                .basePriceUsd(BigDecimal.valueOf(599.99))
                .supplier(Supplier.builder()
                        .supplierId("sup-002")
                        .name("DisplayCorp")
                        .leadTimeDays(14)
                        .build())
                .build();

        StepVerifier.create(useCase.execute(input))
                .assertNext(saved -> {
                    assertNotNull(saved.getProductId());
                    assertFalse(saved.getProductId().isBlank());
                    assertEquals(ProductStatus.VALIDANDO_PROVEEDOR, saved.getStatus());
                    assertNotNull(saved.getCreatedAt());
                    assertNotNull(saved.getUpdatedAt());
                    assertEquals("Monitor 4K", saved.getName());
                    assertEquals("MONITORES", saved.getCategory());
                    assertEquals("sup-002", saved.getSupplier().getSupplierId());
                })
                .verifyComplete();

        verify(repository, times(1)).save(any(Product.class));
        verify(eventPublisher, times(1)).publish(eq("catalog.product-validated"), anyString(), any());
    }

    @Test
    @DisplayName("Respeta productId si ya viene en el input")
    void shouldPreserveExistingProductId() {
        when(repository.save(any(Product.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        Product input = Product.builder()
                .productId("custom-id-123")
                .name("Teclado Custom")
                .category("TECLADOS")
                .basePriceUsd(BigDecimal.TEN)
                .build();

        StepVerifier.create(useCase.execute(input))
                .assertNext(saved ->
                        assertEquals("custom-id-123", saved.getProductId()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Propaga error del repositorio al caller")
    void shouldPropagateRepositoryError() {
        when(repository.save(any(Product.class)))
                .thenReturn(Mono.error(new RuntimeException("MongoDB caído")));

        Product input = Product.builder()
                .name("Producto fallido")
                .category("TEST")
                .basePriceUsd(BigDecimal.ONE)
                .build();

        StepVerifier.create(useCase.execute(input))
                .expectErrorMatches(e ->
                        e instanceof RuntimeException
                                && e.getMessage().contains("MongoDB caído"))
                .verify();

        verify(eventPublisher, never()).publish(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("El producto guardado pasa por save() con los campos correctos")
    void shouldCallSaveWithCorrectFields() {
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        when(repository.save(captor.capture()))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        Product input = Product.builder()
                .name("Mouse Pro")
                .category("PERIFÉRICOS")
                .basePriceUsd(BigDecimal.valueOf(49.99))
                .taxRate(BigDecimal.valueOf(0.19))
                .minOrderQty(10)
                .maxOrderQty(500)
                .build();

        StepVerifier.create(useCase.execute(input)).expectNextCount(1).verifyComplete();

        Product captured = captor.getValue();
        assertEquals(ProductStatus.VALIDANDO_PROVEEDOR, captured.getStatus());
        assertEquals("Mouse Pro", captured.getName());
        assertEquals(10, captured.getMinOrderQty());
        assertEquals(500, captured.getMaxOrderQty());
    }
}
