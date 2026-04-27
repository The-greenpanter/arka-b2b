package co.com.bancolombia.usecase.provider;

import co.com.bancolombia.model.provider.Provider;
import co.com.bancolombia.model.provider.gateways.ProviderRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class GetProviderUseCase {

    private final ProviderRepository repository;

    public Mono<Provider> execute(String providerId) {
        return repository.findById(providerId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Provider not found: " + providerId)));
    }
}
