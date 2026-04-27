package co.com.bancolombia.r2dbc;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface PaymentR2dbcRepository extends R2dbcRepository<PaymentEntity, String> {

    Mono<PaymentEntity> findByOrderId(String orderId);
}
