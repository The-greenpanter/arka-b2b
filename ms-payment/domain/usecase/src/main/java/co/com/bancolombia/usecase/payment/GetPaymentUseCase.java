package co.com.bancolombia.usecase.payment;
import co.com.bancolombia.model.payment.Payment;
import co.com.bancolombia.model.payment.gateways.PaymentRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class GetPaymentUseCase {
    private final PaymentRepository repository;
    public Mono<Payment> execute(String paymentId) {
        return repository.findById(paymentId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Payment not found: " + paymentId)));
    }
}
