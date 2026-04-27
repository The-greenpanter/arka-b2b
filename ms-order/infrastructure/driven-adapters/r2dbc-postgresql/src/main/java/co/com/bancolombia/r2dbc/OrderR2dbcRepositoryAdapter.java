package co.com.bancolombia.r2dbc;
import co.com.bancolombia.model.order.Order;
import co.com.bancolombia.model.order.gateways.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class OrderR2dbcRepositoryAdapter implements OrderRepository {
    private final OrderR2dbcRepository repository;

    @Override public Mono<Order> save(Order o) {
        return repository.save(OrderEntity.fromDomain(o)).map(OrderEntity::toDomain);
    }
    @Override public Mono<Order> findById(String id) {
        return repository.findById(id).map(OrderEntity::toDomain);
    }
    @Override public Flux<Order> findByCustomerId(String customerId) {
        return repository.findByCustomerId(customerId).map(OrderEntity::toDomain);
    }
}
