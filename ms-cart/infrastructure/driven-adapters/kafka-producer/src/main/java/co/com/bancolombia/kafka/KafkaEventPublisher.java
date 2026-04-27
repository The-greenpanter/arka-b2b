package co.com.bancolombia.kafka;

import co.com.bancolombia.model.cart.gateways.EventPublisher;
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
    private final KafkaTemplate<String, String> cartKafkaTemplate;
    private final ObjectMapper cartObjectMapper;

    @Override
    public Mono<Void> publish(String topic, String key, Object payload) {
        return Mono.fromCallable(() -> cartObjectMapper.writeValueAsString(payload))
                .flatMap(json -> Mono.fromFuture(() ->
                        cartKafkaTemplate.send(topic, key, json).toCompletableFuture()))
                .doOnNext(r -> log.info("Event published → topic={} key={}", topic, key))
                .then();
    }
}
