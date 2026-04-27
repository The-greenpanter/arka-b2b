package co.com.bancolombia.usecase.stock;

import co.com.bancolombia.model.stock.Stock;
import co.com.bancolombia.model.stock.gateways.StockRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Use case: añade nueva cantidad disponible al stock de un producto.
 *
 * Se ejecuta cuando:
 *  1. Una orden es cancelada (libera reserva)
 *  2. Una compra a proveedor llega (incrementa disponible)
 *  3. Un rebalance manual del admin
 *
 * Siempre garantiza status = ACTIVE después de ejecutar.
 *
 * Análogo biológico: la "recarga" de vesículas de neurotransmisores
 * en la terminal presináptica (sintetizadas en el soma, transportadas
 * por el axón, liberadas en la sinapsis).
 */
@RequiredArgsConstructor
public class RestockUseCase {

    private final StockRepository repository;

    /**
     * @param productId el ID del producto
     * @param quantity la cantidad a añadir al disponible
     * @return Mono<Stock> con el stock actualizado tras el restock
     * @throws IllegalArgumentException si no existe stock
     * @throws IllegalArgumentException si quantity <= 0
     */
    public Mono<Stock> execute(String productId, Integer quantity) {
        return repository.findByProductId(productId)
                .switchIfEmpty(Mono.error(
                        new IllegalArgumentException("Stock not found for product: " + productId)))
                .map(stock -> stock.restock(quantity))
                .flatMap(repository::save);
    }
}
