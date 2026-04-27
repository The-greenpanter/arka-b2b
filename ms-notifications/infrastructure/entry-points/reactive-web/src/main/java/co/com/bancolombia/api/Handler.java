package co.com.bancolombia.api;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class Handler {
    public Mono<ServerResponse> health(ServerRequest request) {
        return ServerResponse.ok().bodyValue("ms-notifications OK");
    }
}
