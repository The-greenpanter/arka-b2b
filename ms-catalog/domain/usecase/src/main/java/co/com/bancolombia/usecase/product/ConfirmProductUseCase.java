package co.com.bancolombia.usecase.product;

import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.gateways.ProductRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Saga HU1 paso 3: recibe inventory.stock-created.
 * Transición: EN_CREACION_STOCK → CONFIRMADO.
 */
@RequiredArgsConstructor
public class ConfirmProductUseCase {

    private final ProductRepository repository;

    public Mono<Product> execute(String productId) {
        return repository.findById(productId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Product no encontrado: " + productId)))
                .map(Product::confirm)
                .flatMap(repository::save);
    }
}
