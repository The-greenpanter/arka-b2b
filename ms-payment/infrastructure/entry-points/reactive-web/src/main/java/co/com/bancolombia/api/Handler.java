package co.com.bancolombia.api;

import co.com.bancolombia.usecase.payment.GetPaymentUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class Handler {

    private final GetPaymentUseCase getPaymentUseCase;

    public Mono<ServerResponse> getPayment(ServerRequest request) {
        String paymentId = request.pathVariable("paymentId");
        return getPaymentUseCase.execute(paymentId)
                .flatMap(payment -> ServerResponse.ok().bodyValue(payment))
                .onErrorResume(IllegalArgumentException.class,
                        e -> ServerResponse.notFound().build())
                .onErrorResume(e -> {
                    log.error("Error retrieving payment {}: {}", paymentId, e.getMessage());
                    return ServerResponse.status(500).bodyValue("Internal server error");
                });
    }
}
