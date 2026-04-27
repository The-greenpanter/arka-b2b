package co.com.bancolombia.usecase.stock;

import co.com.bancolombia.model.stock.Stock;
import co.com.bancolombia.model.stock.gateways.EventPublisher;
import co.com.bancolombia.model.stock.gateways.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateStockUseCaseTest {

    @Mock
    private StockRepository repository;

    @Mock
    private EventPublisher eventPublisher;

    private CreateStockUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateStockUseCase(repository, eventPublisher);
        when(eventPublisher.publish(anyString(), anyString(), any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Crea stock vacío para el productId y emite inventory.stock-created")
    void shouldCreateStockAndEmitEvent() {
        when(repository.save(any(Stock.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute("prod-001"))
                .assertNext(stock -> {
                    assertNotNull(stock.getStockId());
                    assertEquals("prod-001", stock.getProductId());
                    assertEquals(0, stock.getAvailableQty());
                    assertEquals(0, stock.getReservedQty());
                    assertNotNull(stock.getCreatedAt());
                    assertNotNull(stock.getUpdatedAt());
                })
                .verifyComplete();

        verify(repository, times(1)).save(any(Stock.class));
        verify(eventPublisher, times(1)).publish(eq("inventory.stock-created"), eq("prod-001"), any());
    }

    @Test
    @DisplayName("Propaga error del repositorio")
    void shouldPropagateRepositoryError() {
        when(repository.save(any(Stock.class)))
                .thenReturn(Mono.error(new RuntimeException("DB down")));

        StepVerifier.create(useCase.execute("prod-002"))
                .expectErrorMatches(e -> e instanceof RuntimeException && e.getMessage().contains("DB down"))
                .verify();

        verify(eventPublisher, never()).publish(anyString(), anyString(), any());
    }
}
