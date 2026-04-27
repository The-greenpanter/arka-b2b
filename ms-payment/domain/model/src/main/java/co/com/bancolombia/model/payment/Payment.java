package co.com.bancolombia.model.payment;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Getter @Setter @Builder(toBuilder=true) @AllArgsConstructor @NoArgsConstructor
public class Payment {
    private String paymentId;
    private String orderId;
    private BigDecimal amount;
    private PaymentStatus status;
    private String failureReason;
    private Instant createdAt;
    private Instant processedAt;

    public Payment markProcessed() {
        this.status = PaymentStatus.PROCESSED;
        this.processedAt = Instant.now();
        return this;
    }

    public Payment markFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
        this.processedAt = Instant.now();
        return this;
    }
}
