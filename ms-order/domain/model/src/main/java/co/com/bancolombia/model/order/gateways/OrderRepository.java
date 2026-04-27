package co.com.bancolombia.model.order.gateways;
import co.com.bancolombia.model.order.Order;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
public interface OrderRepository {
    Mono<Order> save(Order order);
    Mono<Order> findById(String orderId);
    Flux<Order> findByCustomerId(String customerId);
}
