package co.com.bancolombia.api;

import co.com.bancolombia.model.cart.CartItem;
import co.com.bancolombia.usecase.cart.AddItemUseCase;
import co.com.bancolombia.usecase.cart.CheckoutUseCase;
import co.com.bancolombia.usecase.cart.GetCartUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;

@Component
@RequiredArgsConstructor
public class Handler {

    private final GetCartUseCase getCartUseCase;
    private final AddItemUseCase addItemUseCase;
    private final CheckoutUseCase checkoutUseCase;

    public Mono<ServerResponse> getCart(ServerRequest req) {
        return getCartUseCase.execute(req.pathVariable("cartId"))
                .flatMap(cart -> ServerResponse.ok().bodyValue(cart))
                .onErrorResume(IllegalArgumentException.class,
                        e -> ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> addItem(ServerRequest req) {
        String customerId = req.pathVariable("customerId");
        return req.bodyToMono(CartItem.class)
                .flatMap(item -> addItemUseCase.execute(customerId, item))
                .flatMap(cart -> ServerResponse.ok().bodyValue(cart));
    }

    public Mono<ServerResponse> checkout(ServerRequest req) {
        return checkoutUseCase.execute(req.pathVariable("cartId"))
                .flatMap(cart -> ServerResponse.created(URI.create("/api/carts/" + cart.getCartId()))
                        .bodyValue(cart))
                .onErrorResume(IllegalArgumentException.class,
                        e -> ServerResponse.notFound().build())
                .onErrorResume(IllegalStateException.class,
                        e -> ServerResponse.status(409).bodyValue(e.getMessage()));
    }
}
