package co.com.bancolombia.config;

import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.ProductStatus;
import co.com.bancolombia.model.product.gateways.ProductRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementación en memoria del puerto ProductRepository.
 *
 * Sirve para que el microservicio arranque y los endpoints REST funcionen
 * SIN necesidad de levantar MongoDB. Útil para los primeros pasos del
 * proyecto, demos y pruebas locales rápidas.
 *
 * Se activa cuando arka.catalog.use-in-memory-repository=true en
 * application.yaml. Cambiando ese flag a false, Spring activa el
 * MongoProductRepositoryAdapter en su lugar.
 */
@Component
@ConditionalOnProperty(
        prefix = "arka.catalog",
        name = "use-in-memory-repository",
        havingValue = "true",
        matchIfMissing = true
)
public class InMemoryProductRepository implements ProductRepository {

    private final Map<String, Product> store = new ConcurrentHashMap<>();

    @Override
    public Mono<Product> save(Product product) {
        return Mono.fromSupplier(() -> {
            store.put(product.getProductId(), product);
            return product;
        });
    }

    @Override
    public Mono<Product> findById(String productId) {
        Product p = store.get(productId);
        return p == null ? Mono.empty() : Mono.just(p);
    }

    @Override
    public Flux<Product> findAll() {
        return Flux.fromIterable(store.values());
    }

    @Override
    public Flux<Product> findByStatus(ProductStatus status) {
        return Flux.fromIterable(store.values())
                .filter(p -> p.getStatus() == status);
    }

    @Override
    public Flux<Product> findByCategory(String category) {
        return Flux.fromIterable(store.values())
                .filter(p -> category.equalsIgnoreCase(p.getCategory()));
    }

    @Override
    public Mono<Boolean> existsById(String productId) {
        return Mono.just(store.containsKey(productId));
    }
}
