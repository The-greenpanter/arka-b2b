package co.com.bancolombia.adapter;

import co.com.bancolombia.model.notification.Notification;
import co.com.bancolombia.model.notification.gateways.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class LogNotificationSender implements NotificationSender {
    private static final Logger log = LoggerFactory.getLogger(LogNotificationSender.class);

    @Override
    public Mono<Void> send(Notification notification) {
        log.info("[NOTIFICATION] type={} recipient={} message={}",
                notification.getType(), notification.getRecipientId(), notification.getMessage());
        return Mono.empty();
    }
}
