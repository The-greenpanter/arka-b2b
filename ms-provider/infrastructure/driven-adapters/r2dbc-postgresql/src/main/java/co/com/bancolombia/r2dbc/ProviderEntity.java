package co.com.bancolombia.r2dbc;

import co.com.bancolombia.model.provider.Provider;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;

@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
@Table("providers")
public class ProviderEntity {
    @Id private String providerId;
    private String name;
    private String contactEmail;
    private String phone;
    private Integer leadTimeDays;
    private Boolean active;
    private Instant createdAt;

    public Provider toDomain() {
        return Provider.builder()
                .providerId(providerId)
                .name(name)
                .contactEmail(contactEmail)
                .phone(phone)
                .leadTimeDays(leadTimeDays)
                .active(active)
                .createdAt(createdAt)
                .build();
    }

    public static ProviderEntity fromDomain(Provider p) {
        return ProviderEntity.builder()
                .providerId(p.getProviderId())
                .name(p.getName())
                .contactEmail(p.getContactEmail())
                .phone(p.getPhone())
                .leadTimeDays(p.getLeadTimeDays())
                .active(p.getActive())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
