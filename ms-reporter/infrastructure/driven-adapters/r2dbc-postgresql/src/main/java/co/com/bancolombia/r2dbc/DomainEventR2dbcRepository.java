package co.com.bancolombia.r2dbc;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface DomainEventR2dbcRepository extends R2dbcRepository<DomainEventEntity, String> {

    Flux<DomainEventEntity> findByEventType(String eventType);

    Flux<DomainEventEntity> findByAggregateId(String aggregateId);
}
