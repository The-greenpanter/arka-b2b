package co.com.bancolombia.config;

import co.com.bancolombia.model.product.gateways.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * EventPublisher no-op para desarrollo sin Kafka.
 * Activo cuando arka.catalog.use-in-memory-broker=true (default).
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "arka.catalog",
        name = "use-in-memory-broker",
        havingValue = "true",
        matchIfMissing = true
)
public class InMemoryEventPublisher implements EventPublisher {

    @Override
    public Mono<Void> publish(String topic, String key, Object payload) {
        log.info("[IN-MEMORY-BROKER] topic={} key={}", topic, key);
        return Mono.empty();
    }
}
