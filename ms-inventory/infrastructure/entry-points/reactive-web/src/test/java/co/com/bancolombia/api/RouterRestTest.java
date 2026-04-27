package co.com.bancolombia.api;

import co.com.bancolombia.model.stock.Stock;
import co.com.bancolombia.model.stock.StockStatus;
import co.com.bancolombia.usecase.stock.CreateStockUseCase;
import co.com.bancolombia.usecase.stock.GetStockUseCase;
import co.com.bancolombia.usecase.stock.ReserveStockUseCase;
import co.com.bancolombia.usecase.stock.RestockUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
@DisplayName("RouterRestTest for Stock API")
class RouterRestTest {

    @Autowired
    private WebTestClient webClient;

    @MockitoBean
    private CreateStockUseCase createStockUseCase;

    @MockitoBean
    private GetStockUseCase getStockUseCase;

    @MockitoBean
    private ReserveStockUseCase reserveStockUseCase;

    @MockitoBean
    private RestockUseCase restockUseCase;

    private Stock sampleStock() {
        return Stock.builder()
                .stockId("stock-001")
                .productId("prod-001")
                .availableQty(100)
                .reservedQty(0)
                .status(StockStatus.ACTIVE)
                .createdAt(Instant.parse("2026-04-12T10:00:00Z"))
                .updatedAt(Instant.parse("2026-04-12T10:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("POST /api/stocks → 201 Created con stockId")
    void shouldCreateStock() {
        when(createStockUseCase.execute("prod-001"))
                .thenReturn(Mono.just(sampleStock()));

        webClient.post()
                .uri("/api/stocks")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "productId": "prod-001"
                        }
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.stockId").isEqualTo("stock-001")
                .jsonPath("$.productId").isEqualTo("prod-001")
                .jsonPath("$.availableQty").isEqualTo(100)
                .jsonPath("$.status").isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("GET /api/stocks/{productId} → 200 OK con detalles del stock")
    void shouldGetStock() {
        when(getStockUseCase.execute("prod-001"))
                .thenReturn(Mono.just(sampleStock()));

        webClient.get()
                .uri("/api/stocks/prod-001")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.productId").isEqualTo("prod-001")
                .jsonPath("$.availableQty").isEqualTo(100)
                .jsonPath("$.totalQty").isEqualTo(100);
    }

    @Test
    @DisplayName("GET /api/stocks/{productId} → 404 si no existe")
    void shouldReturn404WhenStockNotFound() {
        when(getStockUseCase.execute("no-existe"))
                .thenReturn(Mono.error(new IllegalArgumentException("Stock not found")));

        webClient.get()
                .uri("/api/stocks/no-existe")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("POST /api/stocks/{productId}/reserve → 200 OK con stock reservado")
    void shouldReserveStock() {
        Stock reserved = sampleStock().toBuilder()
                .availableQty(70)
                .reservedQty(30)
                .updatedAt(Instant.now())
                .build();

        when(reserveStockUseCase.execute(eq("prod-001"), anyInt()))
                .thenReturn(Mono.just(reserved));

        webClient.post()
                .uri("/api/stocks/prod-001/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "quantity": 30
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.availableQty").isEqualTo(70)
                .jsonPath("$.reservedQty").isEqualTo(30)
                .jsonPath("$.totalQty").isEqualTo(100);
    }

    @Test
    @DisplayName("POST /api/stocks/{productId}/reserve → 409 Conflict si no hay suficiente")
    void shouldReturn409WhenInsufficientStock() {
        when(reserveStockUseCase.execute(eq("prod-001"), anyInt()))
                .thenReturn(Mono.error(new IllegalStateException(
                        "Insufficient available quantity")));

        webClient.post()
                .uri("/api/stocks/prod-001/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "quantity": 200
                        }
                        """)
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    @DisplayName("POST /api/stocks/{productId}/restock → 200 OK con stock incrementado")
    void shouldRestockStock() {
        Stock restocked = sampleStock().toBuilder()
                .availableQty(150)
                .updatedAt(Instant.now())
                .build();

        when(restockUseCase.execute(eq("prod-001"), anyInt()))
                .thenReturn(Mono.just(restocked));

        webClient.post()
                .uri("/api/stocks/prod-001/restock")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "quantity": 50
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.availableQty").isEqualTo(150)
                .jsonPath("$.totalQty").isEqualTo(150);
    }

    @Test
    @DisplayName("POST /api/stocks/{productId}/restock → 400 Bad Request si cantidad inválida")
    void shouldReturn400WhenInvalidRestockQuantity() {
        when(restockUseCase.execute(eq("prod-001"), anyInt()))
                .thenReturn(Mono.error(new IllegalArgumentException(
                        "Restock quantity must be positive")));

        webClient.post()
                .uri("/api/stocks/prod-001/restock")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "quantity": 0
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }
}
