package co.com.bancolombia.model.notification;
import lombok.*;
import java.time.Instant;

@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class Notification {
    private String notificationId;
    private NotificationType type;
    private String recipientId;
    private String message;
    private Instant sentAt;
}
