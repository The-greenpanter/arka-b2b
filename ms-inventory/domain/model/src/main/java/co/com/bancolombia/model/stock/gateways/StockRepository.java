package co.com.bancolombia.model.stock.gateways;

import co.com.bancolombia.model.stock.Stock;
import reactor.core.publisher.Mono;

/**
 * Output Port (interfaz, parte del DOMINIO).
 *
 * Analogía biológica: receptor de membrana. El dominio expone el "qué"
 * (puedo guardar, buscar stocks), y los adapters externos
 * (R2DBC/PostgreSQL, en memoria, etc.) implementan el "cómo".
 *
 * El dominio NUNCA importa Spring, ni R2DBC, ni JPA. Solo Reactor
 * (Mono/Flux) que es agnóstico de transporte.
 */
public interface StockRepository {

    Mono<Stock> save(Stock stock);

    Mono<Stock> findById(String stockId);

    Mono<Stock> findByProductId(String productId);

    Mono<Boolean> existsById(String stockId);

    Mono<Boolean> existsByProductId(String productId);
}
