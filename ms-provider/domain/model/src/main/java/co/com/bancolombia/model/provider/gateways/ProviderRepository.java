package co.com.bancolombia.model.provider.gateways;

import co.com.bancolombia.model.provider.Provider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ProviderRepository {
    Mono<Provider> save(Provider provider);
    Mono<Provider> findById(String providerId);
    Flux<Provider> findAll();
}
