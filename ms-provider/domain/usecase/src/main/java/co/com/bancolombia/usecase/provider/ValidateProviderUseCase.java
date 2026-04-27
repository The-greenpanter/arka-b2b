package co.com.bancolombia.usecase.provider;

import co.com.bancolombia.model.provider.Provider;
import co.com.bancolombia.model.provider.ProviderStatus;
import co.com.bancolombia.model.provider.gateways.EventPublisher;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.UUID;

/**
 * Saga HU1: recibe productId desde catalog.product-validated,
 * stub-validates the provider, emits provider.provider-validated.
 */
@RequiredArgsConstructor
public class ValidateProviderUseCase {

    private final EventPublisher eventPublisher;

    public Mono<Provider> execute(String productId) {
        return Mono.fromSupplier(() -> Provider.builder()
                        .providerId(UUID.randomUUID().toString())
                        .productId(productId)
                        .status(ProviderStatus.VALIDATED)
                        .validatedAt(Instant.now())
                        .build())
                .flatMap(provider -> eventPublisher
                        .publish("provider.provider-validated", provider.getProductId(), provider)
                        .thenReturn(provider));
    }
}
