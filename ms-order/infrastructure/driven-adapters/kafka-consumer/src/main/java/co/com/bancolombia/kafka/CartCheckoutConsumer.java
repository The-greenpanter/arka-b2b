package co.com.bancolombia.kafka;

import co.com.bancolombia.model.order.Order;
import co.com.bancolombia.model.order.OrderStatus;
import co.com.bancolombia.usecase.order.CreateOrderUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CartCheckoutConsumer {
    private final CreateOrderUseCase createOrderUseCase;

    @KafkaListener(topics = "cart.checkout-requested", groupId = "ms-order",
            containerFactory = "orderKafkaListenerContainerFactory")
    public void onCartCheckout(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String cartId = record.key();
        log.info("cart.checkout-requested received: cartId={}", cartId);
        Order order = Order.builder()
                .orderId(UUID.randomUUID().toString())
                .cartId(cartId)
                .customerId("unknown")
                .status(OrderStatus.PENDING)
                .items(List.of())
                .totalAmount(BigDecimal.ZERO)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        createOrderUseCase.execute(order)
                .doOnSuccess(o -> { log.info("Order created: {}", o.getOrderId()); ack.acknowledge(); })
                .doOnError(e -> log.error("Failed to create order for cart {}: {}", cartId, e.getMessage()))
                .subscribe();
    }
}
