package co.com.bancolombia.r2dbc;

import co.com.bancolombia.model.provider.Provider;
import co.com.bancolombia.model.provider.gateways.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ProviderR2dbcRepositoryAdapter implements ProviderRepository {

    private final ProviderR2dbcRepository repository;

    @Override
    public Mono<Provider> save(Provider provider) {
        return repository.save(ProviderEntity.fromDomain(provider)).map(ProviderEntity::toDomain);
    }

    @Override
    public Mono<Provider> findById(String providerId) {
        return repository.findById(providerId).map(ProviderEntity::toDomain);
    }

    @Override
    public Flux<Provider> findAll() {
        return repository.findAll().map(ProviderEntity::toDomain);
    }
}
