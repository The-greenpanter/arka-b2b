package co.com.bancolombia.usecase.order;

import co.com.bancolombia.model.order.Order;
import co.com.bancolombia.model.order.OrderStatus;
import co.com.bancolombia.model.order.gateways.EventPublisher;
import co.com.bancolombia.model.order.gateways.OrderRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class CreateOrderUseCase {
    private final OrderRepository repository;
    private final EventPublisher eventPublisher;

    public Mono<Order> execute(Order input) {
        return Mono.just(input.toBuilder().status(OrderStatus.PENDING).build())
                .flatMap(repository::save)
                .flatMap(saved -> eventPublisher
                        .publish("order.order-created", saved.getOrderId(), saved)
                        .thenReturn(saved));
    }
}
