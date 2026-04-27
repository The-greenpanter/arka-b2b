package co.com.bancolombia.r2dbc;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface ProviderR2dbcRepository extends ReactiveCrudRepository<ProviderEntity, String> {
}
