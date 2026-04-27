package co.com.bancolombia.model.provider;

import lombok.*;
import java.time.Instant;

@Getter @Setter @Builder(toBuilder=true) @AllArgsConstructor @NoArgsConstructor
public class Provider {
    private String providerId;
    private String productId;
    private ProviderStatus status;
    private String rejectionReason;
    private Instant validatedAt;
}
