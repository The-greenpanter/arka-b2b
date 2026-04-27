package co.com.bancolombia.r2dbc;

import co.com.bancolombia.model.stock.Stock;
import co.com.bancolombia.model.stock.gateways.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Implementación del puerto StockRepository usando R2DBC/PostgreSQL.
 *
 * Se activa cuando arka.inventory.use-in-memory-repository=false en
 * application.yaml. Requiere que PostgreSQL esté corriendo y accesible
 * vía spring.r2dbc.url.
 *
 * Mapea llamadas del dominio (Mono<Stock>) a llamadas R2DBC
 * (Mono<StockEntity>) y viceversa.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "arka.inventory",
        name = "use-in-memory-repository",
        havingValue = "false"
)
public class StockR2dbcRepositoryAdapter implements StockRepository {

    private final StockR2dbcRepository r2dbcRepository;

    @Override
    public Mono<Stock> save(Stock stock) {
        return r2dbcRepository.save(StockEntity.fromDomain(stock))
                .map(StockEntity::toDomain);
    }

    @Override
    public Mono<Stock> findById(String stockId) {
        return r2dbcRepository.findById(stockId)
                .map(StockEntity::toDomain);
    }

    @Override
    public Mono<Stock> findByProductId(String productId) {
        return r2dbcRepository.findByProductId(productId)
                .map(StockEntity::toDomain);
    }

    @Override
    public Mono<Boolean> existsById(String stockId) {
        return r2dbcRepository.existsById(stockId);
    }

    @Override
    public Mono<Boolean> existsByProductId(String productId) {
        return r2dbcRepository.existsByProductId(productId);
    }
}
