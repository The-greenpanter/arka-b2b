package co.com.bancolombia.kafka;

import co.com.bancolombia.usecase.order.CompleteOrderUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProcessedConsumer {

    private final CompleteOrderUseCase completeOrderUseCase;

    @KafkaListener(
            topics = "payment.payment-processed",
            groupId = "ms-order",
            containerFactory = "orderKafkaListenerContainerFactory"
    )
    public void onPaymentProcessed(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String orderId = record.key();
        log.info("payment.payment-processed received: orderId={}", orderId);
        completeOrderUseCase.execute(orderId)
                .doOnSuccess(o -> {
                    log.info("Order completed: orderId={}", o.getOrderId());
                    ack.acknowledge();
                })
                .doOnError(e -> log.error("Failed to complete order {}: {}", orderId, e.getMessage()))
                .subscribe();
    }
}
