package co.com.bancolombia.model.payment.gateways;
import reactor.core.publisher.Mono;
public interface EventPublisher {
    Mono<Void> publish(String topic, String key, Object payload);
}
