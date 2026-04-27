package co.com.bancolombia.mongo;

import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.ProductStatus;
import co.com.bancolombia.model.product.gateways.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Adapter REAL: implementa el port del dominio usando MongoDB reactivo.
 *
 * Se activa solo si arka.catalog.use-in-memory-repository=false (o ausente
 * cuando havingValue="false" está configurado), de modo que el proyecto
 * pueda arrancar sin Mongo durante el desarrollo inicial.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "arka.catalog",
        name = "use-in-memory-repository",
        havingValue = "false",
        matchIfMissing = false
)
public class MongoProductRepositoryAdapter implements ProductRepository {

    private final ProductMongoRepository repository;

    @Override
    public Mono<Product> save(Product product) {
        return repository.save(ProductDocument.fromDomain(product))
                .map(ProductDocument::toDomain);
    }

    @Override
    public Mono<Product> findById(String productId) {
        return repository.findById(productId).map(ProductDocument::toDomain);
    }

    @Override
    public Flux<Product> findAll() {
        return repository.findAll().map(ProductDocument::toDomain);
    }

    @Override
    public Flux<Product> findByStatus(ProductStatus status) {
        return repository.findByStatus(status).map(ProductDocument::toDomain);
    }

    @Override
    public Flux<Product> findByCategory(String category) {
        return repository.findByCategory(category).map(ProductDocument::toDomain);
    }

    @Override
    public Mono<Boolean> existsById(String productId) {
        return repository.existsById(productId);
    }
}
