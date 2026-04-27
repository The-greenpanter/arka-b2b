package co.com.bancolombia.config;

import co.com.bancolombia.model.stock.Stock;
import co.com.bancolombia.model.stock.gateways.StockRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementación en memoria del puerto StockRepository.
 *
 * Sirve para que el microservicio arranque y los endpoints REST funcionen
 * SIN necesidad de levantar PostgreSQL. Útil para los primeros pasos del
 * proyecto, demos y pruebas locales rápidas.
 *
 * Se activa cuando arka.inventory.use-in-memory-repository=true en
 * application.yaml (por defecto). Cambiando ese flag a false, Spring
 * activa el StockR2dbcRepositoryAdapter en su lugar.
 *
 * Almacena stocks en un ConcurrentHashMap indexado por stockId y productId.
 */
@Component
@ConditionalOnProperty(
        prefix = "arka.inventory",
        name = "use-in-memory-repository",
        havingValue = "true",
        matchIfMissing = true
)
public class InMemoryStockRepository implements StockRepository {

    private final Map<String, Stock> storeById = new ConcurrentHashMap<>();
    private final Map<String, Stock> storeByProductId = new ConcurrentHashMap<>();

    @Override
    public Mono<Stock> save(Stock stock) {
        return Mono.fromSupplier(() -> {
            storeById.put(stock.getStockId(), stock);
            storeByProductId.put(stock.getProductId(), stock);
            return stock;
        });
    }

    @Override
    public Mono<Stock> findById(String stockId) {
        Stock s = storeById.get(stockId);
        return s == null ? Mono.empty() : Mono.just(s);
    }

    @Override
    public Mono<Stock> findByProductId(String productId) {
        Stock s = storeByProductId.get(productId);
        return s == null ? Mono.empty() : Mono.just(s);
    }

    @Override
    public Mono<Boolean> existsById(String stockId) {
        return Mono.just(storeById.containsKey(stockId));
    }

    @Override
    public Mono<Boolean> existsByProductId(String productId) {
        return Mono.just(storeByProductId.containsKey(productId));
    }
}
