package co.com.bancolombia.kafka;

import co.com.bancolombia.usecase.payment.ProcessPaymentUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedConsumer {

    private final ProcessPaymentUseCase processPaymentUseCase;

    @KafkaListener(
            topics = "order.order-created",
            groupId = "ms-payment",
            containerFactory = "paymentKafkaListenerContainerFactory"
    )
    public void onOrderCreated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String orderId = record.key();
        log.info("order.order-created received: orderId={}", orderId);
        processPaymentUseCase.execute(orderId, BigDecimal.ZERO)
                .doOnSuccess(p -> {
                    log.info("Payment processed for order {}: paymentId={}", orderId, p.getPaymentId());
                    ack.acknowledge();
                })
                .doOnError(e -> log.error("Failed to process payment for order {}: {}", orderId, e.getMessage()))
                .subscribe();
    }
}
