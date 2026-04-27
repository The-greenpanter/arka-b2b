package co.com.bancolombia.api;

import co.com.bancolombia.usecase.report.GetEventsUseCase;
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

    private final GetEventsUseCase getEventsUseCase;

    public Mono<ServerResponse> getEventsByType(ServerRequest request) {
        String type = request.pathVariable("type");
        return ServerResponse.ok()
                .body(getEventsUseCase.byType(type), co.com.bancolombia.model.report.DomainEvent.class)
                .onErrorResume(e -> {
                    log.error("Error retrieving events by type {}: {}", type, e.getMessage());
                    return ServerResponse.status(500).bodyValue("Internal server error");
                });
    }

    public Mono<ServerResponse> getEventsByAggregate(ServerRequest request) {
        String aggregateId = request.pathVariable("id");
        return ServerResponse.ok()
                .body(getEventsUseCase.byAggregate(aggregateId), co.com.bancolombia.model.report.DomainEvent.class)
                .onErrorResume(e -> {
                    log.error("Error retrieving events by aggregate {}: {}", aggregateId, e.getMessage());
                    return ServerResponse.status(500).bodyValue("Internal server error");
                });
    }
}
