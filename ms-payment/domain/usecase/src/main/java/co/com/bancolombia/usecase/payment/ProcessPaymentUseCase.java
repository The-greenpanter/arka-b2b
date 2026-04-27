package co.com.bancolombia.usecase.payment;

import co.com.bancolombia.model.payment.Payment;
import co.com.bancolombia.model.payment.PaymentStatus;
import co.com.bancolombia.model.payment.gateways.EventPublisher;
import co.com.bancolombia.model.payment.gateways.PaymentRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class ProcessPaymentUseCase {
    private final PaymentRepository repository;
    private final EventPublisher eventPublisher;

    public Mono<Payment> execute(String orderId, java.math.BigDecimal amount) {
        Payment payment = Payment.builder()
                .paymentId(UUID.randomUUID().toString())
                .orderId(orderId)
                .amount(amount)
                .status(PaymentStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        payment.markProcessed(); // stub: always succeeds
        return repository.save(payment)
                .flatMap(saved -> eventPublisher
                        .publish("payment.payment-processed", saved.getOrderId(), saved)
                        .thenReturn(saved));
    }
}
