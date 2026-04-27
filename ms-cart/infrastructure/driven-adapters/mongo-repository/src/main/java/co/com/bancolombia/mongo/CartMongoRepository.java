package co.com.bancolombia.mongo;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface CartMongoRepository extends ReactiveMongoRepository<CartDocument, String> {
    Mono<CartDocument> findByCustomerId(String customerId);
}
