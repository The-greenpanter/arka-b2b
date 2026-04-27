package co.com.bancolombia.usecase.provider;

import co.com.bancolombia.model.provider.Provider;
import co.com.bancolombia.model.provider.gateways.ProviderRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class RegisterProviderUseCase {

    private final ProviderRepository repository;

    public Mono<Provider> execute(Provider input) {
        Provider provider = input.toBuilder()
                .providerId(UUID.randomUUID().toString())
                .active(true)
                .createdAt(Instant.now())
                .build();
        return repository.save(provider);
    }
}
