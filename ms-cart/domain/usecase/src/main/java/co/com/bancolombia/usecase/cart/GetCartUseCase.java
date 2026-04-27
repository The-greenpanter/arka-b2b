package co.com.bancolombia.usecase.cart;

import co.com.bancolombia.model.cart.Cart;
import co.com.bancolombia.model.cart.gateways.CartRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class GetCartUseCase {
    private final CartRepository repository;

    public Mono<Cart> execute(String cartId) {
        return repository.findById(cartId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Cart not found: " + cartId)));
    }
}
