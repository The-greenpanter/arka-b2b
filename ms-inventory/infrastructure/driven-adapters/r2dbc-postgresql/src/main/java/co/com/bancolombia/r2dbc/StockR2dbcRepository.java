package co.com.bancolombia.r2dbc;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Spring Data R2DBC Repository para StockEntity.
 *
 * Proporciona métodos de acceso a la BD con Reactive Streams.
 * NO es el puerto del dominio (ese es StockRepository en :model),
 * sino un auxiliar específico de R2DBC.
 */
@Repository
public interface StockR2dbcRepository extends R2dbcRepository<StockEntity, String> {

    Mono<StockEntity> findByProductId(String productId);

    Mono<Boolean> existsByProductId(String productId);
}
