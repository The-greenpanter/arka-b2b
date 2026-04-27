package co.com.bancolombia.usecase.provider;

import co.com.bancolombia.model.provider.Provider;
import co.com.bancolombia.model.provider.ProviderStatus;
import co.com.bancolombia.model.provider.gateways.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValidateProviderUseCaseTest {

    @Mock
    private EventPublisher eventPublisher;

    private ValidateProviderUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ValidateProviderUseCase(eventPublisher);
        when(eventPublisher.publish(anyString(), anyString(), any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Validates provider and emits provider.provider-validated")
    void shouldValidateAndEmitEvent() {
        StepVerifier.create(useCase.execute("prod-001"))
                .assertNext(provider -> {
                    assertNotNull(provider.getProviderId());
                    assertEquals("prod-001", provider.getProductId());
                    assertEquals(ProviderStatus.VALIDATED, provider.getStatus());
                    assertNotNull(provider.getValidatedAt());
                })
                .verifyComplete();

        verify(eventPublisher).publish(eq("provider.provider-validated"), eq("prod-001"), any());
    }
}
