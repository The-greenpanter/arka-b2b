package co.com.bancolombia.r2dbc;

import co.com.bancolombia.model.payment.Payment;
import co.com.bancolombia.model.payment.PaymentStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Table("payments")
public class PaymentEntity implements Persistable<String> {

    @Id
    @Column("payment_id")
    private String paymentId;
    @Transient @Builder.Default private boolean isNew = true;

    @Column("order_id")
    private String orderId;

    @Column("amount")
    private BigDecimal amount;

    @Column("status")
    private String status;

    @Column("failure_reason")
    private String failureReason;

    @Column("created_at")
    private Instant createdAt;

    @Column("processed_at")
    private Instant processedAt;

    @Override
    public String getId() { return paymentId; }

    public static PaymentEntity fromDomain(Payment payment) {
        return PaymentEntity.builder()
                .paymentId(payment.getPaymentId())
                .orderId(payment.getOrderId())
                .amount(payment.getAmount())
                .status(payment.getStatus().name())
                .failureReason(payment.getFailureReason())
                .createdAt(payment.getCreatedAt())
                .processedAt(payment.getProcessedAt())
                .build();
    }

    public Payment toDomain() {
        return Payment.builder()
                .paymentId(this.paymentId)
                .orderId(this.orderId)
                .amount(this.amount)
                .status(PaymentStatus.valueOf(this.status))
                .failureReason(this.failureReason)
                .createdAt(this.createdAt)
                .processedAt(this.processedAt)
                .build();
    }
}
