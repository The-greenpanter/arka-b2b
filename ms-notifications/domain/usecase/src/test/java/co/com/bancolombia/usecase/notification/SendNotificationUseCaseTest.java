package co.com.bancolombia.usecase.notification;
import co.com.bancolombia.model.notification.*;
import co.com.bancolombia.model.notification.gateways.NotificationSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.time.Instant;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SendNotificationUseCaseTest {
    @Mock NotificationSender sender;

    @Test void shouldDelegateSending() {
        when(sender.send(any())).thenReturn(Mono.empty());
        SendNotificationUseCase useCase = new SendNotificationUseCase(sender);
        Notification n = Notification.builder().notificationId("n-001")
                .type(NotificationType.ORDER_CREATED).recipientId("cust").message("msg").sentAt(Instant.now()).build();
        StepVerifier.create(useCase.execute(n)).verifyComplete();
        verify(sender).send(n);
    }
}
