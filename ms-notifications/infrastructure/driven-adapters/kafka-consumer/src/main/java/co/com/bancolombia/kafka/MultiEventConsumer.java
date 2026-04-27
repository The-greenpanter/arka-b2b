package co.com.bancolombia.kafka;

import co.com.bancolombia.model.notification.Notification;
import co.com.bancolombia.model.notification.NotificationType;
import co.com.bancolombia.usecase.notification.SendNotificationUseCase;
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
public class MultiEventConsumer {

    private final SendNotificationUseCase sendNotificationUseCase;

    @KafkaListener(
            topics = "order.order-created,order.order-completed,payment.payment-processed,payment.payment-failed,inventory.stock-low-alert",
            groupId = "ms-notifications",
            containerFactory = "notificationsKafkaListenerContainerFactory"
    )
    public void onDomainEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String topic = record.topic();
        String aggregateId = record.key();
        log.info("Event received: topic={} aggregateId={}", topic, aggregateId);

        NotificationType type = resolveType(topic);
        String message = buildMessage(topic, aggregateId);

        Notification notification = Notification.builder()
                .notificationId(UUID.randomUUID().toString())
                .type(type)
                .recipientId(aggregateId)
                .message(message)
                .sentAt(Instant.now())
                .build();

        sendNotificationUseCase.execute(notification)
                .doOnSuccess(v -> {
                    log.info("Notification sent for event topic={} aggregateId={}", topic, aggregateId);
                    ack.acknowledge();
                })
                .doOnError(e -> log.error("Failed to send notification for topic={}: {}", topic, e.getMessage()))
                .subscribe();
    }

    private NotificationType resolveType(String topic) {
        return switch (topic) {
            case "order.order-created" -> NotificationType.ORDER_CREATED;
            case "order.order-completed" -> NotificationType.ORDER_COMPLETED;
            case "payment.payment-processed" -> NotificationType.PAYMENT_PROCESSED;
            case "payment.payment-failed" -> NotificationType.PAYMENT_FAILED;
            case "inventory.stock-low-alert" -> NotificationType.STOCK_LOW_ALERT;
            default -> NotificationType.ORDER_CREATED;
        };
    }

    private String buildMessage(String topic, String aggregateId) {
        return switch (topic) {
            case "order.order-created" -> "Your order " + aggregateId + " has been created";
            case "order.order-completed" -> "Your order " + aggregateId + " has been completed";
            case "payment.payment-processed" -> "Payment for order " + aggregateId + " was processed successfully";
            case "payment.payment-failed" -> "Payment for order " + aggregateId + " failed";
            case "inventory.stock-low-alert" -> "[URGENT] Stock crítico para producto " + aggregateId + ". Iniciar reabastecimiento.";
            default -> "Event received for " + aggregateId;
        };
    }
}
