package co.com.bancolombia.usecase.order;

import co.com.bancolombia.model.order.Order;
import co.com.bancolombia.model.order.gateways.OrderRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class GetOrderUseCase {
    private final OrderRepository repository;

    public Mono<Order> execute(String orderId) {
        return repository.findById(orderId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Order not found: " + orderId)));
    }
}
