package co.com.bancolombia.usecase.product;

import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.gateways.ProductRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class GetProductUseCase {

    private final ProductRepository repository;

    public Mono<Product> execute(String productId) {
        return repository.findById(productId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Product no encontrado: " + productId)));
    }
}
