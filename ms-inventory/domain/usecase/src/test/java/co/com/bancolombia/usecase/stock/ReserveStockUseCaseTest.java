package co.com.bancolombia.usecase.stock;

import co.com.bancolombia.model.stock.Stock;
import co.com.bancolombia.model.stock.StockStatus;
import co.com.bancolombia.model.stock.gateways.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests del use case ReserveStockUseCase.
 *
 * @ExtendWith(MockitoExtension.class) configura Mockito automáticamente.
 * @InjectMocks y @Mock inyectan el use case con repositorio mockeado.
 *
 * StepVerifier proporciona una forma fluida de verificar Mono/Flux.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReserveStockUseCase")
class ReserveStockUseCaseTest {

    @Mock
    private StockRepository repository;

    @InjectMocks
    private ReserveStockUseCase useCase;

    private Stock sampleStock;

    @BeforeEach
    void setUp() {
        sampleStock = Stock.builder()
                .stockId("stock-001")
                .productId("prod-001")
                .availableQty(100)
                .reservedQty(0)
                .status(StockStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("execute() reserva exitosa → availableQty baja")
    void shouldReserveSuccessfully() {
        Stock reserved = sampleStock.toBuilder()
                .availableQty(70)
                .reservedQty(30)
                .updatedAt(Instant.now())
                .build();

        when(repository.findByProductId("prod-001"))
                .thenReturn(Mono.just(sampleStock));
        when(repository.save(any(Stock.class)))
                .thenReturn(Mono.just(reserved));

        StepVerifier.create(useCase.execute("prod-001", 30))
                .assertNext(stock -> {
                    assert stock.getAvailableQty() == 70;
                    assert stock.getReservedQty() == 30;
                    assert stock.getStatus() == StockStatus.ACTIVE;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("execute() con qty insuficiente → error IllegalStateException")
    void shouldFailWhenInsufficientStock() {
        when(repository.findByProductId("prod-001"))
                .thenReturn(Mono.just(sampleStock));

        StepVerifier.create(useCase.execute("prod-001", 200))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    @DisplayName("execute() producto no existe → error IllegalArgumentException")
    void shouldFailWhenStockNotFound() {
        when(repository.findByProductId("no-existe"))
                .thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("no-existe", 50))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    @DisplayName("execute() que agota stock transiciona a DEPLETED")
    void shouldTransitionToDepletedWhenExhausted() {
        Stock depleted = sampleStock.toBuilder()
                .availableQty(0)
                .reservedQty(100)
                .status(StockStatus.DEPLETED)
                .updatedAt(Instant.now())
                .build();

        when(repository.findByProductId("prod-001"))
                .thenReturn(Mono.just(sampleStock));
        when(repository.save(any(Stock.class)))
                .thenReturn(Mono.just(depleted));

        StepVerifier.create(useCase.execute("prod-001", 100))
                .assertNext(stock -> {
                    assert stock.getStatus() == StockStatus.DEPLETED;
                    assert stock.getAvailableQty() == 0;
                })
                .verifyComplete();
    }
}
