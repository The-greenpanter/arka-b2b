package co.com.bancolombia.usecase.product;

import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.ProductStatus;
import co.com.bancolombia.model.product.gateways.ProductRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class ListProductsUseCase {

    private final ProductRepository repository;

    public Flux<Product> execute(ProductStatus status, String category) {
        if (status != null) return repository.findByStatus(status);
        if (category != null) return repository.findByCategory(category);
        return repository.findAll();
    }
}
