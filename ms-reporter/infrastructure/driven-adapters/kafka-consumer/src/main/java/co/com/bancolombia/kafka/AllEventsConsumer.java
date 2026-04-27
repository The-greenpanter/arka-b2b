package co.com.bancolombia.kafka;

import co.com.bancolombia.model.report.DomainEvent;
import co.com.bancolombia.usecase.report.RecordEventUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AllEventsConsumer {

    private final RecordEventUseCase recordEventUseCase;

    @KafkaListener(
            topics = "catalog.product-validated,inventory.stock-created,order.order-created,order.order-completed,payment.payment-processed",
            groupId = "ms-reporter",
            containerFactory = "reporterKafkaListenerContainerFactory"
    )
    public void onDomainEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String topic = record.topic();
        String aggregateId = record.key();
        String payload = record.value();
        log.info("Domain event received for reporting: topic={} aggregateId={}", topic, aggregateId);

        DomainEvent event = DomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(topic)
                .aggregateId(aggregateId)
                .payload(payload != null ? payload : "{}")
                .occurredAt(Instant.now())
                .build();

        recordEventUseCase.execute(event)
                .doOnSuccess(saved -> {
                    log.info("Event recorded: eventId={} eventType={}", saved.getEventId(), saved.getEventType());
                    ack.acknowledge();
                })
                .doOnError(e -> log.error("Failed to record event topic={}: {}", topic, e.getMessage()))
                .subscribe();
    }
}
