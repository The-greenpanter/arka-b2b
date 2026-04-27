package co.com.bancolombia.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class RouterRest {
    @Bean
    public RouterFunction<ServerResponse> routerFunction(Handler handler) {
        return RouterFunctions.route()
                .GET("/api/carts/{cartId}", handler::getCart)
                .POST("/api/carts/{customerId}/items", handler::addItem)
                .POST("/api/carts/{cartId}/checkout", handler::checkout)
                .build();
    }
}
