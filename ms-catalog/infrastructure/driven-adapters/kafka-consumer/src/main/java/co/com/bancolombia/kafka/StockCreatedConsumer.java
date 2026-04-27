package co.com.bancolombia.kafka;

import co.com.bancolombia.usecase.product.ConfirmProductUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Saga HU1 step 3: ms-inventory creó el stock inicial.
 * Mueve el producto EN_CREACION_STOCK → CONFIRMADO.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockCreatedConsumer {

    private final ConfirmProductUseCase confirmProductUseCase;

    @KafkaListener(
            topics = "inventory.stock-created",
            groupId = "ms-catalog",
            containerFactory = "catalogKafkaListenerContainerFactory"
    )
    public void onStockCreated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String productId = record.key();
        log.info("inventory.stock-created received: productId={}", productId);
        confirmProductUseCase.execute(productId)
                .doOnSuccess(p -> {
                    log.info("Product CONFIRMADO: {}", productId);
                    ack.acknowledge();
                })
                .doOnError(e -> log.error("Failed to confirm product {}: {}", productId, e.getMessage()))
                .subscribe();
    }
}
