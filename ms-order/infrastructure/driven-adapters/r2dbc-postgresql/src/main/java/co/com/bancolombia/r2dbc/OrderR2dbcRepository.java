package co.com.bancolombia.r2dbc;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
public interface OrderR2dbcRepository extends ReactiveCrudRepository<OrderEntity, String> {
    Flux<OrderEntity> findByCustomerId(String customerId);
}
