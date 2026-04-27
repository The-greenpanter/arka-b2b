package co.com.bancolombia.kafka;

import co.com.bancolombia.usecase.provider.ValidateProviderUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductValidatedConsumer {

    private final ValidateProviderUseCase validateProviderUseCase;

    @KafkaListener(
            topics = "catalog.product-validated",
            groupId = "ms-provider",
            containerFactory = "providerKafkaListenerContainerFactory"
    )
    public void onProductValidated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String productId = record.key();
        log.info("catalog.product-validated received: productId={}", productId);
        validateProviderUseCase.execute(productId)
                .doOnSuccess(p -> {
                    log.info("Provider validated for product {}", productId);
                    ack.acknowledge();
                })
                .doOnError(e -> log.error("Failed to validate provider for {}: {}", productId, e.getMessage()))
                .subscribe();
    }
}
