package co.com.bancolombia.usecase.stock;

import co.com.bancolombia.model.stock.Stock;
import co.com.bancolombia.model.stock.gateways.StockRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Use case: obtiene un stock por su productId.
 *
 * Responde a consultas GET /api/stocks/{productId} para verificar
 * disponibilidad antes de hacer reservas.
 *
 * Analogía biológica: la "consulta" al sistema nervioso para preguntar
 * si hay neurotrasmisores disponibles antes de intentar una sinapsis.
 */
@RequiredArgsConstructor
public class GetStockUseCase {

    private final StockRepository repository;

    /**
     * @param productId el ID del producto
     * @return Mono<Stock> con los detalles del stock
     * @throws IllegalArgumentException si no existe
     */
    public Mono<Stock> execute(String productId) {
        return repository.findByProductId(productId)
                .switchIfEmpty(Mono.error(
                        new IllegalArgumentException("Stock not found for product: " + productId)));
    }
}
