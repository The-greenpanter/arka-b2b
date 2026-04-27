package co.com.bancolombia.usecase.stock;

import co.com.bancolombia.model.stock.Stock;
import co.com.bancolombia.model.stock.gateways.StockRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Use case: añade nueva cantidad disponible al stock de un producto.
 *
 * HU3: si el stock supera el umbral tras el restock, se resetea alertSent
 * para que la próxima caída vuelva a disparar la alerta.
 */
@RequiredArgsConstructor
public class RestockUseCase {

    private final StockRepository repository;

    public Mono<Stock> execute(String productId, Integer quantity) {
        return repository.findByProductId(productId)
                .switchIfEmpty(Mono.error(
                        new IllegalArgumentException("Stock not found for product: " + productId)))
                .map(stock -> {
                    stock.restock(quantity);
                    int threshold = stock.getMinimumThreshold() != null ? stock.getMinimumThreshold() : 10;
                    if (Boolean.TRUE.equals(stock.getAlertSent()) && stock.getAvailableQty() > threshold) {
                        stock.resetAlert();
                    }
                    return stock;
                })
                .flatMap(repository::save);
    }
}
