package co.com.bancolombia.usecase.stock;

import co.com.bancolombia.model.stock.Stock;
import co.com.bancolombia.model.stock.gateways.EventPublisher;
import co.com.bancolombia.model.stock.gateways.StockRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Saga HU1 paso 2: crea stock vacío y emite inventory.stock-created.
 */
@RequiredArgsConstructor
public class CreateStockUseCase {

    private final StockRepository repository;
    private final EventPublisher eventPublisher;

    public Mono<Stock> execute(String productId) {
        return Mono.fromSupplier(() -> {
                    Instant now = Instant.now();
                    return Stock.builder()
                            .stockId(UUID.randomUUID().toString())
                            .productId(productId)
                            .availableQty(0)
                            .reservedQty(0)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                })
                .flatMap(repository::save)
                .flatMap(saved -> eventPublisher
                        .publish("inventory.stock-created", saved.getProductId(), saved)
                        .thenReturn(saved));
    }
}
