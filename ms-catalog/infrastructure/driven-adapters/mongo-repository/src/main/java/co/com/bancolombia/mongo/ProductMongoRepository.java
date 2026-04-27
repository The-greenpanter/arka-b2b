package co.com.bancolombia.mongo;

import co.com.bancolombia.model.product.ProductStatus;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

/**
 * Repositorio Spring Data Mongo (genera implementación en runtime).
 * Es un detalle del adapter, NO está en el dominio.
 */
public interface ProductMongoRepository extends ReactiveMongoRepository<ProductDocument, String> {

    Flux<ProductDocument> findByStatus(ProductStatus status);

    Flux<ProductDocument> findByCategory(String category);
}
