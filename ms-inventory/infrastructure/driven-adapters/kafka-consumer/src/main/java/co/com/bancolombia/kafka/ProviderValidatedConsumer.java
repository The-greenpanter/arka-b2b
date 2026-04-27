package co.com.bancolombia.kafka;

import co.com.bancolombia.usecase.stock.CreateStockUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Saga HU1 step 2: ms-provider validó el proveedor.
 * ms-inventory crea el stock inicial (qty=0) y emite inventory.stock-created.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderValidatedConsumer {

    private final CreateStockUseCase createStockUseCase;

    @KafkaListener(
            topics = "provider.provider-validated",
            groupId = "ms-inventory",
            containerFactory = "inventoryKafkaListenerContainerFactory"
    )
    public void onProviderValidated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String productId = record.key();
        log.info("provider.provider-validated received: productId={}", productId);
        createStockUseCase.execute(productId)
                .doOnSuccess(stock -> {
                    log.info("Stock created for product {}: stockId={}", productId, stock.getStockId());
                    ack.acknowledge();
                })
                .doOnError(e -> log.error("Failed to create stock for {}: {}", productId, e.getMessage()))
                .subscribe();
    }
}
