package co.com.bancolombia.model.payment.gateways;
import co.com.bancolombia.model.payment.Payment;
import reactor.core.publisher.Mono;
public interface PaymentRepository {
    Mono<Payment> save(Payment payment);
    Mono<Payment> findById(String paymentId);
    Mono<Payment> findByOrderId(String orderId);
}
