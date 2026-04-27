package co.com.bancolombia.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.RouterFunctions.nest;
import static org.springframework.web.reactive.function.server.RequestPredicates.path;

/**
 * Functional routing — declara las rutas REST sin @RestController.
 *
 * Endpoints expuestos:
 *   POST   /api/stocks                      → crea stock (HU1 saga paso 2)
 *   GET    /api/stocks/{productId}          → detalle de stock
 *   POST   /api/stocks/{productId}/reserve  → reserva cantidad (HU2)
 *   POST   /api/stocks/{productId}/restock  → añade cantidad disponible
 */
@Configuration
public class RouterRest {

    @Bean
    public RouterFunction<ServerResponse> stockRoutes(Handler handler) {
        return nest(path("/api/stocks"),
                route(POST("").and(accept(MediaType.APPLICATION_JSON)), handler::createStock)
                        .andRoute(GET("/{productId}"), handler::getStock)
                        .andRoute(POST("/{productId}/reserve").and(accept(MediaType.APPLICATION_JSON)), handler::reserveStock)
                        .andRoute(POST("/{productId}/restock").and(accept(MediaType.APPLICATION_JSON)), handler::restockStock)
        );
    }
}
