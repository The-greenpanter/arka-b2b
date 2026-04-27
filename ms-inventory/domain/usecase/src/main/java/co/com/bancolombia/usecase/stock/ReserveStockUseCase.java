package co.com.bancolombia.usecase.stock;

import co.com.bancolombia.model.stock.Stock;
import co.com.bancolombia.model.stock.gateways.StockRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Use case: reserva una cantidad de stock para un producto.
 *
 * Esta es la operación crítica en HU2 (Ordering): cuando un cliente
 * crea una orden, el inventario debe reservar la cantidad solicitada.
 *
 * La reserva es una promesa: "esta cantidad está apartada para ti".
 * Si luego el pago falla o la orden se cancela, la reserva se libera.
 *
 * Analogía biológica: el "receptor" ocupa neurotransmisores (binding),
 * lo que evita que otros receptores los usen. Si la señal se cancela,
 * se liberan.
 */
@RequiredArgsConstructor
public class ReserveStockUseCase {

    private final StockRepository repository;

    /**
     * @param productId el ID del producto
     * @param quantity la cantidad a reservar
     * @return Mono<Stock> con el stock actualizado tras la reserva
     * @throws IllegalArgumentException si no existe stock
     * @throws IllegalStateException si no hay suficiente disponible o está DEPLETED
     */
    public Mono<Stock> execute(String productId, Integer quantity) {
        return repository.findByProductId(productId)
                .switchIfEmpty(Mono.error(
                        new IllegalArgumentException("Stock not found for product: " + productId)))
                .map(stock -> stock.reserve(quantity))
                .flatMap(repository::save);
    }
}
