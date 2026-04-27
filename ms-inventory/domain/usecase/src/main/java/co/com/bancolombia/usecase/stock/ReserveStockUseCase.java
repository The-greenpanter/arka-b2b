package co.com.bancolombia.usecase.stock;

import co.com.bancolombia.model.stock.Stock;
import co.com.bancolombia.model.stock.StockLowAlertEvent;
import co.com.bancolombia.model.stock.gateways.EventPublisher;
import co.com.bancolombia.model.stock.gateways.StockRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Use case: reserva una cantidad de stock para un producto.
 *
 * HU3: después de cada reserva comprueba si el stock cae bajo el umbral.
 * Si sí, emite inventory.stock-low-alert (una sola vez por ciclo, debounced por alertSent).
 */
@RequiredArgsConstructor
public class ReserveStockUseCase {

    private final StockRepository repository;
    private final EventPublisher eventPublisher;

    public Mono<Stock> execute(String productId, Integer quantity) {
        return repository.findByProductId(productId)
                .switchIfEmpty(Mono.error(
                        new IllegalArgumentException("Stock not found for product: " + productId)))
                .flatMap(stock -> {
                    stock.reserve(quantity);
                    boolean shouldAlert = stock.isLowStock();
                    if (shouldAlert) {
                        stock.markAlertSent();
                    }
                    return repository.save(stock)
                            .flatMap(saved -> {
                                if (shouldAlert) {
                                    StockLowAlertEvent event = StockLowAlertEvent.builder()
                                            .productId(saved.getProductId())
                                            .providerId(saved.getProviderId())
                                            .currentStock(saved.getAvailableQty())
                                            .minimumThreshold(saved.getMinimumThreshold())
                                            .triggerReason("ORDER_RESERVATION")
                                            .timestamp(Instant.now())
                                            .build();
                                    return eventPublisher
                                            .publish("inventory.stock-low-alert", saved.getProductId(), event)
                                            .thenReturn(saved);
                                }
                                return Mono.just(saved);
                            });
                });
    }
}
