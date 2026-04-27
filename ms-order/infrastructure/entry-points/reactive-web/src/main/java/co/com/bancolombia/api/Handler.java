package co.com.bancolombia.api;
import co.com.bancolombia.usecase.order.CompleteOrderUseCase;
import co.com.bancolombia.usecase.order.GetOrderUseCase;
import co.com.bancolombia.usecase.order.GetOrdersByCustomerUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class Handler {
    private final GetOrderUseCase getOrderUseCase;
    private final CompleteOrderUseCase completeOrderUseCase;
    private final GetOrdersByCustomerUseCase getOrdersByCustomerUseCase;

    public Mono<ServerResponse> getOrder(ServerRequest req) {
        return getOrderUseCase.execute(req.pathVariable("orderId"))
                .flatMap(o -> ServerResponse.ok().bodyValue(o))
                .onErrorResume(IllegalArgumentException.class, e -> ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> completeOrder(ServerRequest req) {
        return completeOrderUseCase.execute(req.pathVariable("orderId"))
                .flatMap(o -> ServerResponse.ok().bodyValue(o))
                .onErrorResume(IllegalArgumentException.class, e -> ServerResponse.notFound().build())
                .onErrorResume(IllegalStateException.class, e -> ServerResponse.status(409).bodyValue(e.getMessage()));
    }

    /** HU5: historial de órdenes del cliente autenticado (by customerId query param). */
    public Mono<ServerResponse> getOrdersByCustomer(ServerRequest req) {
        String customerId = req.queryParam("customerId").orElseThrow(
                () -> new IllegalArgumentException("customerId is required"));
        int page = req.queryParam("page").map(Integer::parseInt).orElse(0);
        int size = req.queryParam("size").map(Integer::parseInt).orElse(10);
        return ServerResponse.ok()
                .body(getOrdersByCustomerUseCase.execute(customerId, page, size),
                        co.com.bancolombia.model.order.Order.class);
    }
}
