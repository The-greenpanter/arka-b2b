package co.com.bancolombia.config;

import co.com.bancolombia.model.stock.gateways.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "arka.inventory",
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
