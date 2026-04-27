package co.com.bancolombia.usecase.notification;

import co.com.bancolombia.model.notification.Notification;
import co.com.bancolombia.model.notification.gateways.NotificationSender;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class SendNotificationUseCase {
    private final NotificationSender sender;

    public Mono<Void> execute(Notification notification) {
        return sender.send(notification);
    }
}
