package co.com.bancolombia.model.product.gateways;

import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.ProductStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Output Port (interfaz, parte del DOMINIO).
 *
 * Analogía biológica: receptor de membrana. El dominio expone el "qué"
 * (puedo guardar, buscar, listar productos), y los adapters externos
 * (MongoDB, en memoria, etc.) implementan el "cómo".
 *
 * El dominio NUNCA importa Spring, ni Mongo, ni JPA. Solo Reactor
 * (Mono/Flux) que es agnóstico de transporte.
 */
public interface ProductRepository {

    Mono<Product> save(Product product);

    Mono<Product> findById(String productId);

    Flux<Product> findAll();

    Flux<Product> findByStatus(ProductStatus status);

    Flux<Product> findByCategory(String category);

    Mono<Boolean> existsById(String productId);
}
