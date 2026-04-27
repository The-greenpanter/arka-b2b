package co.com.bancolombia.usecase.order;

import co.com.bancolombia.model.order.Order;
import co.com.bancolombia.model.order.gateways.EventPublisher;
import co.com.bancolombia.model.order.gateways.OrderRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class CompleteOrderUseCase {
    private final OrderRepository repository;
    private final EventPublisher eventPublisher;

    public Mono<Order> execute(String orderId) {
        return repository.findById(orderId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Order not found: " + orderId)))
                .map(Order::complete)
                .flatMap(repository::save)
                .flatMap(saved -> eventPublisher
                        .publish("order.order-completed", saved.getOrderId(), saved)
                        .thenReturn(saved));
    }
}
