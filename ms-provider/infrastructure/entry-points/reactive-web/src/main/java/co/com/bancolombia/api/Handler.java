package co.com.bancolombia.api;

import co.com.bancolombia.model.provider.Provider;
import co.com.bancolombia.usecase.provider.GetProviderUseCase;
import co.com.bancolombia.usecase.provider.ListProvidersUseCase;
import co.com.bancolombia.usecase.provider.RegisterProviderUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;

@Component
@RequiredArgsConstructor
public class Handler {

    private final RegisterProviderUseCase registerProviderUseCase;
    private final GetProviderUseCase getProviderUseCase;
    private final ListProvidersUseCase listProvidersUseCase;

    public Mono<ServerResponse> createProvider(ServerRequest request) {
        return request.bodyToMono(Provider.class)
                .flatMap(registerProviderUseCase::execute)
                .flatMap(p -> ServerResponse
                        .created(URI.create("/api/providers/" + p.getProviderId()))
                        .bodyValue(p));
    }

    public Mono<ServerResponse> getProvider(ServerRequest request) {
        return getProviderUseCase.execute(request.pathVariable("id"))
                .flatMap(p -> ServerResponse.ok().bodyValue(p))
                .onErrorResume(IllegalArgumentException.class,
                        e -> ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> listProviders(ServerRequest request) {
        return ServerResponse.ok().body(listProvidersUseCase.execute(), Provider.class);
    }
}
