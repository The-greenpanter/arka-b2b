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
 *   POST   /api/products             → registra producto (HU1 paso 1)
 *   GET    /api/products             → lista (?status=, ?category=)
 *   GET    /api/products/{id}        → detalle
 *   POST   /api/products/{id}/confirm → confirmación manual (atajo HU1)
 */
@Configuration
public class RouterRest {

    @Bean
    public RouterFunction<ServerResponse> productRoutes(Handler handler) {
        return nest(path("/api/products"),
                route(POST("").and(accept(MediaType.APPLICATION_JSON)), handler::createProduct)
                        .andRoute(GET(""), handler::listProducts)
                        .andRoute(GET("/{id}"), handler::getProduct)
                        .andRoute(POST("/{id}/confirm"), handler::confirmProduct)
        );
    }
}
