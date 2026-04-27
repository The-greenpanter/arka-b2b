package co.com.bancolombia.usecase.product;

import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.ProductStatus;
import co.com.bancolombia.model.product.gateways.EventPublisher;
import co.com.bancolombia.model.product.gateways.ProductRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Saga HU1 paso 1: registra el producto y dispara catalog.product-validated.
 * Transición: (nuevo) → EN_CREACION → VALIDANDO_PROVEEDOR + evento Kafka.
 */
@RequiredArgsConstructor
public class RegisterProductUseCase {

    private final ProductRepository repository;
    private final EventPublisher eventPublisher;

    public Mono<Product> execute(Product input) {
        return Mono.fromSupplier(() -> {
                    Instant now = Instant.now();
                    return input.toBuilder()
                            .productId(input.getProductId() != null ? input.getProductId() : UUID.randomUUID().toString())
                            .status(ProductStatus.EN_CREACION)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                })
                .map(Product::validateProvider)
                .flatMap(repository::save)
                .flatMap(saved -> eventPublisher
                        .publish("catalog.product-validated", saved.getProductId(), saved)
                        .thenReturn(saved));
    }
}
