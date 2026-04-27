package co.com.bancolombia.usecase.cart;

import co.com.bancolombia.model.cart.Cart;
import co.com.bancolombia.model.cart.CartItem;
import co.com.bancolombia.model.cart.CartStatus;
import co.com.bancolombia.model.cart.gateways.CartRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class AddItemUseCase {
    private final CartRepository repository;

    public Mono<Cart> execute(String customerId, CartItem item) {
        return repository.findByCustomerId(customerId)
                .switchIfEmpty(Mono.fromSupplier(() -> Cart.builder()
                        .cartId(UUID.randomUUID().toString())
                        .customerId(customerId)
                        .status(CartStatus.OPEN)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build()))
                .map(cart -> cart.addItem(item))
                .flatMap(repository::save);
    }
}
