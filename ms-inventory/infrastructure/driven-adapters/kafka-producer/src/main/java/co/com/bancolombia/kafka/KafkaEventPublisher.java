package co.com.bancolombia.kafka;

import co.com.bancolombia.model.stock.gateways.EventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, String> inventoryKafkaTemplate;
    private final ObjectMapper inventoryObjectMapper;

    @Override
    public Mono<Void> publish(String topic, String key, Object payload) {
        return Mono.fromCallable(() -> inventoryObjectMapper.writeValueAsString(payload))
                .flatMap(json -> Mono.fromFuture(() ->
                        inventoryKafkaTemplate.send(topic, key, json).toCompletableFuture()))
                .doOnNext(r -> log.info("Event published → topic={} key={}", topic, key))
                .doOnError(e -> log.error("Failed to publish → topic={} key={} error={}", topic, key, e.getMessage()))
                .then();
    }
}
