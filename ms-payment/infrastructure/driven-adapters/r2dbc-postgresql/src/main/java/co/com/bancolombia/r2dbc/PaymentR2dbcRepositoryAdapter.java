package co.com.bancolombia.r2dbc;

import co.com.bancolombia.model.payment.Payment;
import co.com.bancolombia.model.payment.gateways.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class PaymentR2dbcRepositoryAdapter implements PaymentRepository {

    private final PaymentR2dbcRepository r2dbcRepository;

    @Override
    public Mono<Payment> save(Payment payment) {
        return r2dbcRepository.save(PaymentEntity.fromDomain(payment))
                .map(PaymentEntity::toDomain);
    }

    @Override
    public Mono<Payment> findById(String paymentId) {
        return r2dbcRepository.findById(paymentId)
                .map(PaymentEntity::toDomain);
    }

    @Override
    public Mono<Payment> findByOrderId(String orderId) {
        return r2dbcRepository.findByOrderId(orderId)
                .map(PaymentEntity::toDomain);
    }
}
