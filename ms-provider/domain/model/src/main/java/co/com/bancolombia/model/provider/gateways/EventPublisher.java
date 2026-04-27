package co.com.bancolombia.model.provider.gateways;
import reactor.core.publisher.Mono;
public interface EventPublisher {
    Mono<Void> publish(String topic, String key, Object payload);
}
