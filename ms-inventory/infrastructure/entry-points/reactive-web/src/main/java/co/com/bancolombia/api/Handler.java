package co.com.bancolombia.api;

import co.com.bancolombia.api.dto.StockRequest;
import co.com.bancolombia.api.dto.StockResponse;
import co.com.bancolombia.usecase.stock.CreateStockUseCase;
import co.com.bancolombia.usecase.stock.GetStockUseCase;
import co.com.bancolombia.usecase.stock.ReserveStockUseCase;
import co.com.bancolombia.usecase.stock.RestockUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Primary adapter (entry-point HTTP).
 *
 * Inyecta los use cases del módulo :usecase y los expone vía WebFlux
 * functional routing. NO contiene reglas de negocio, solo:
 *  - parsea request → DTO → dominio
 *  - llama al use case
 *  - mapea dominio → response → JSON
 *  - traduce errores a códigos HTTP
 *
 * Endpoints:
 *   POST   /api/stocks                      → CreateStockUseCase (HU1)
 *   GET    /api/stocks/{productId}          → GetStockUseCase
 *   POST   /api/stocks/{productId}/reserve  → ReserveStockUseCase (HU2)
 *   POST   /api/stocks/{productId}/restock  → RestockUseCase
 */
@Component
@RequiredArgsConstructor
public class Handler {

    private final CreateStockUseCase createStockUseCase;
    private final GetStockUseCase getStockUseCase;
    private final ReserveStockUseCase reserveStockUseCase;
    private final RestockUseCase restockUseCase;

    /** POST /api/stocks */
    public Mono<ServerResponse> createStock(ServerRequest request) {
        return request.bodyToMono(StockRequest.class)
                .flatMap(req -> createStockUseCase.execute(req.getProductId()))
                .map(StockResponse::fromDomain)
                .flatMap(resp -> ServerResponse
                        .created(URI.create("/api/stocks/" + resp.getProductId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(resp));
    }

    /** GET /api/stocks/{productId} */
    public Mono<ServerResponse> getStock(ServerRequest request) {
        String productId = request.pathVariable("productId");
        return getStockUseCase.execute(productId)
                .map(StockResponse::fromDomain)
                .flatMap(resp -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(resp))
                .onErrorResume(IllegalArgumentException.class,
                        e -> ServerResponse.notFound().build());
    }

    /** POST /api/stocks/{productId}/reserve */
    public Mono<ServerResponse> reserveStock(ServerRequest request) {
        String productId = request.pathVariable("productId");
        return request.bodyToMono(StockRequest.class)
                .flatMap(req -> reserveStockUseCase.execute(productId, req.getQuantity()))
                .map(StockResponse::fromDomain)
                .flatMap(resp -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(resp))
                .onErrorResume(IllegalArgumentException.class,
                        e -> ServerResponse.notFound().build())
                .onErrorResume(IllegalStateException.class,
                        e -> ServerResponse.status(409).bodyValue(e.getMessage()));
    }

    /** POST /api/stocks/{productId}/restock */
    public Mono<ServerResponse> restockStock(ServerRequest request) {
        String productId = request.pathVariable("productId");
        return request.bodyToMono(StockRequest.class)
                .flatMap(req -> restockUseCase.execute(productId, req.getQuantity()))
                .map(StockResponse::fromDomain)
                .flatMap(resp -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(resp))
                .onErrorResume(IllegalArgumentException.class,
                        e -> ServerResponse.status(400).bodyValue(e.getMessage()))
                .onErrorResume(IllegalStateException.class,
                        e -> ServerResponse.status(409).bodyValue(e.getMessage()));
    }
}
