package co.com.bancolombia.mongo;

import co.com.bancolombia.model.cart.Cart;
import co.com.bancolombia.model.cart.gateways.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MongoCartRepositoryAdapter implements CartRepository {
    private final CartMongoRepository repository;

    @Override
    public Mono<Cart> save(Cart cart) {
        return repository.save(CartDocument.fromDomain(cart)).map(CartDocument::toDomain);
    }

    @Override
    public Mono<Cart> findById(String cartId) {
        return repository.findById(cartId).map(CartDocument::toDomain);
    }

    @Override
    public Mono<Cart> findByCustomerId(String customerId) {
        return repository.findByCustomerId(customerId).map(CartDocument::toDomain);
    }
}
