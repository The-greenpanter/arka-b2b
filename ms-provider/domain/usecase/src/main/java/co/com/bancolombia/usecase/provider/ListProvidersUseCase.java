package co.com.bancolombia.usecase.provider;

import co.com.bancolombia.model.provider.Provider;
import co.com.bancolombia.model.provider.gateways.ProviderRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class ListProvidersUseCase {

    private final ProviderRepository repository;

    public Flux<Provider> execute() {
        return repository.findAll();
    }
}
