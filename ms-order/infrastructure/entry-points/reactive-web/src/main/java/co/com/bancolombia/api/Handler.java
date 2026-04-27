package co.com.bancolombia.api;
import co.com.bancolombia.usecase.order.CompleteOrderUseCase;
import co.com.bancolombia.usecase.order.GetOrderUseCase;
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
}
