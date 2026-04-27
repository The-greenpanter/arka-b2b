package co.com.bancolombia.kafka;

import co.com.bancolombia.usecase.product.HandleProviderValidatedUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Saga HU1 step 2: ms-provider validó el proveedor.
 * Mueve el producto VALIDANDO_PROVEEDOR → EN_CREACION_STOCK.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderValidatedConsumer {

    private final HandleProviderValidatedUseCase handleProviderValidatedUseCase;

    @KafkaListener(
            topics = "provider.provider-validated",
            groupId = "ms-catalog",
            containerFactory = "catalogKafkaListenerContainerFactory"
    )
    public void onProviderValidated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String productId = record.key();
        log.info("provider.provider-validated received: productId={}", productId);
        handleProviderValidatedUseCase.execute(productId)
                .doOnSuccess(p -> {
                    log.info("Product moved to EN_CREACION_STOCK: {}", productId);
                    ack.acknowledge();
                })
                .doOnError(e -> log.error("Failed to handle provider-validated for {}: {}", productId, e.getMessage()))
                .subscribe();
    }
}
