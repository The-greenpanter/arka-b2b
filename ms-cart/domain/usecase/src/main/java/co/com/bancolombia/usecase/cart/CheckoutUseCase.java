package co.com.bancolombia.usecase.cart;

import co.com.bancolombia.model.cart.Cart;
import co.com.bancolombia.model.cart.gateways.CartRepository;
import co.com.bancolombia.model.cart.gateways.EventPublisher;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class CheckoutUseCase {
    private final CartRepository repository;
    private final EventPublisher eventPublisher;

    public Mono<Cart> execute(String cartId) {
        return repository.findById(cartId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Cart not found: " + cartId)))
                .map(Cart::checkout)
                .flatMap(repository::save)
                .flatMap(saved -> eventPublisher
                        .publish("cart.checkout-requested", saved.getCartId(), saved)
                        .thenReturn(saved));
    }
}
