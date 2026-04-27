package co.com.bancolombia.usecase.product;

import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.gateways.ProductRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Saga HU1 step 2: recibe provider.provider-validated.
 * Transición: VALIDANDO_PROVEEDOR → EN_CREACION_STOCK.
 */
@RequiredArgsConstructor
public class HandleProviderValidatedUseCase {

    private final ProductRepository repository;

    public Mono<Product> execute(String productId) {
        return repository.findById(productId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Product no encontrado: " + productId)))
                .map(Product::moveToCreatingStock)
                .flatMap(repository::save);
    }
}
