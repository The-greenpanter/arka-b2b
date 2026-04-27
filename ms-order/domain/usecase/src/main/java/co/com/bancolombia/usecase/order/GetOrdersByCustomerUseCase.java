package co.com.bancolombia.usecase.order;

import co.com.bancolombia.model.order.Order;
import co.com.bancolombia.model.order.gateways.OrderRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/** HU5: historial de órdenes paginado por customerId. */
@RequiredArgsConstructor
public class GetOrdersByCustomerUseCase {
    private final OrderRepository repository;

    public Flux<Order> execute(String customerId, int page, int size) {
        return repository.findByCustomerId(customerId)
                .skip((long) page * size)
                .take(size);
    }
}
