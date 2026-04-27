package co.com.bancolombia.api;

import co.com.bancolombia.api.dto.ProductRequest;
import co.com.bancolombia.api.dto.ProductResponse;
import co.com.bancolombia.model.product.ProductStatus;
import co.com.bancolombia.usecase.product.ConfirmProductUseCase;
import co.com.bancolombia.usecase.product.GetProductUseCase;
import co.com.bancolombia.usecase.product.ListProductsUseCase;
import co.com.bancolombia.usecase.product.RegisterProductUseCase;
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
 */
@Component
@RequiredArgsConstructor
public class Handler {

    private final RegisterProductUseCase registerProductUseCase;
    private final GetProductUseCase getProductUseCase;
    private final ListProductsUseCase listProductsUseCase;
    private final ConfirmProductUseCase confirmProductUseCase;

    /** POST /api/products */
    public Mono<ServerResponse> createProduct(ServerRequest request) {
        return request.bodyToMono(ProductRequest.class)
                .map(ProductRequest::toDomain)
                .flatMap(registerProductUseCase::execute)
                .map(ProductResponse::fromDomain)
                .flatMap(resp -> ServerResponse
                        .created(URI.create("/api/products/" + resp.getProductId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(resp));
    }

    /** GET /api/products/{id} */
    public Mono<ServerResponse> getProduct(ServerRequest request) {
        String id = request.pathVariable("id");
        return getProductUseCase.execute(id)
                .map(ProductResponse::fromDomain)
                .flatMap(resp -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(resp))
                .onErrorResume(IllegalArgumentException.class,
                        e -> ServerResponse.notFound().build());
    }

    /** GET /api/products?status=...&category=... */
    public Mono<ServerResponse> listProducts(ServerRequest request) {
        ProductStatus status = request.queryParam("status")
                .map(String::toUpperCase)
                .map(ProductStatus::valueOf)
                .orElse(null);
        String category = request.queryParam("category").orElse(null);

        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(listProductsUseCase.execute(status, category)
                        .map(ProductResponse::fromDomain), ProductResponse.class);
    }

    /** POST /api/products/{id}/confirm  (atajo manual mientras la saga no existe) */
    public Mono<ServerResponse> confirmProduct(ServerRequest request) {
        String id = request.pathVariable("id");
        return confirmProductUseCase.execute(id)
                .map(ProductResponse::fromDomain)
                .flatMap(resp -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(resp))
                .onErrorResume(IllegalArgumentException.class,
                        e -> ServerResponse.notFound().build())
                .onErrorResume(IllegalStateException.class,
                        e -> ServerResponse.status(409).bodyValue(e.getMessage()));
    }
}
