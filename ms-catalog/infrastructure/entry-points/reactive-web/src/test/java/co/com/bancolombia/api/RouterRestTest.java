package co.com.bancolombia.api;

import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.ProductStatus;
import co.com.bancolombia.model.product.Supplier;
import co.com.bancolombia.usecase.product.ConfirmProductUseCase;
import co.com.bancolombia.usecase.product.GetProductUseCase;
import co.com.bancolombia.usecase.product.ListProductsUseCase;
import co.com.bancolombia.usecase.product.RegisterProductUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Test de integración de la capa HTTP (entry-point).
 *
 * @WebFluxTest levanta SOLO el contexto web (Router + Handler),
 * sin BD, sin Kafka, sin Spring Boot completo.
 * Los use cases se mockean con @MockBean.
 *
 * WebTestClient envía requests HTTP reales al router y verifica
 * status codes, headers y body JSON.
 */
@WebFluxTest
@ContextConfiguration(classes = {RouterRest.class, Handler.class})
class RouterRestTest {

    @Autowired
    private WebTestClient webClient;

    @MockitoBean
    private RegisterProductUseCase registerProductUseCase;

    @MockitoBean
    private GetProductUseCase getProductUseCase;

    @MockitoBean
    private ListProductsUseCase listProductsUseCase;

    @MockitoBean
    private ConfirmProductUseCase confirmProductUseCase;

    private Product sampleProduct() {
        return Product.builder()
                .productId("prod-001")
                .name("Teclado Mecánico Pro")
                .description("Cherry MX Blue")
                .category("TECLADOS")
                .status(ProductStatus.EN_CREACION)
                .basePriceUsd(BigDecimal.valueOf(129.99))
                .taxRate(BigDecimal.valueOf(0.19))
                .minOrderQty(1)
                .maxOrderQty(100)
                .supplier(Supplier.builder()
                        .supplierId("sup-001")
                        .name("TechDistribuidora")
                        .leadTimeDays(7)
                        .build())
                .createdAt(Instant.parse("2026-04-12T10:00:00Z"))
                .updatedAt(Instant.parse("2026-04-12T10:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("POST /api/products → 201 Created con productId")
    void shouldCreateProduct() {
        when(registerProductUseCase.execute(any(Product.class)))
                .thenReturn(Mono.just(sampleProduct()));

        webClient.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "name": "Teclado Mecánico Pro",
                          "description": "Cherry MX Blue",
                          "category": "TECLADOS",
                          "basePriceUsd": 129.99,
                          "taxRate": 0.19,
                          "minOrderQty": 1,
                          "maxOrderQty": 100,
                          "supplier": {
                            "supplierId": "sup-001",
                            "name": "TechDistribuidora",
                            "leadTimeDays": 7
                          }
                        }
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.productId").isEqualTo("prod-001")
                .jsonPath("$.name").isEqualTo("Teclado Mecánico Pro")
                .jsonPath("$.status").isEqualTo("EN_CREACION")
                .jsonPath("$.supplier.supplierId").isEqualTo("sup-001");
    }

    @Test
    @DisplayName("GET /api/products/{id} → 200 OK con producto")
    void shouldGetProductById() {
        when(getProductUseCase.execute("prod-001"))
                .thenReturn(Mono.just(sampleProduct()));

        webClient.get()
                .uri("/api/products/prod-001")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.productId").isEqualTo("prod-001")
                .jsonPath("$.category").isEqualTo("TECLADOS");
    }

    @Test
    @DisplayName("GET /api/products/{id} → 404 si no existe")
    void shouldReturn404WhenNotFound() {
        when(getProductUseCase.execute("no-existe"))
                .thenReturn(Mono.error(new IllegalArgumentException("Product no encontrado")));

        webClient.get()
                .uri("/api/products/no-existe")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("GET /api/products → 200 OK con lista")
    void shouldListProducts() {
        when(listProductsUseCase.execute(null, null))
                .thenReturn(Flux.just(sampleProduct()));

        webClient.get()
                .uri("/api/products")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].productId").isEqualTo("prod-001");
    }

    @Test
    @DisplayName("GET /api/products?status=CONFIRMADO → filtra por status")
    void shouldListByStatus() {
        Product confirmed = sampleProduct().toBuilder()
                .status(ProductStatus.CONFIRMADO)
                .build();

        when(listProductsUseCase.execute(eq(ProductStatus.CONFIRMADO), eq(null)))
                .thenReturn(Flux.just(confirmed));

        webClient.get()
                .uri("/api/products?status=CONFIRMADO")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].status").isEqualTo("CONFIRMADO");
    }

    @Test
    @DisplayName("POST /api/products/{id}/confirm → 200 OK con CONFIRMADO")
    void shouldConfirmProduct() {
        Product confirmed = sampleProduct().toBuilder()
                .status(ProductStatus.CONFIRMADO)
                .build();

        when(confirmProductUseCase.execute("prod-001"))
                .thenReturn(Mono.just(confirmed));

        webClient.post()
                .uri("/api/products/prod-001/confirm")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CONFIRMADO");
    }

    @Test
    @DisplayName("POST /api/products/{id}/confirm → 409 Conflict si ya confirmado")
    void shouldReturn409WhenAlreadyConfirmed() {
        when(confirmProductUseCase.execute("prod-001"))
                .thenReturn(Mono.error(new IllegalStateException(
                        "Transición inválida desde CONFIRMADO")));

        webClient.post()
                .uri("/api/products/prod-001/confirm")
                .exchange()
                .expectStatus().isEqualTo(409);
    }
}
