package co.com.bancolombia.model.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;

/**
 * Value Object que registra cada movimiento de stock (reserva, consumo, restock).
 *
 * Los VOs son inmutables y no tienen identidad de dominio. Se usan para
 * auditar y reconstruir el historial del agregado Stock.
 *
 * Análogo biológico: un "evento de cambio" en el citoplasma que se registra
 * en el cuaderno de observaciones del experimento.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class StockMovement {

    private String movementId;  // UUID generado por el use case
    private String stockId;
    private String productId;
    private MovementType type;  // RESERVE, RELEASE, CONSUME, RESTOCK
    private Integer quantity;
    private String reason;      // "Customer order #123", "Expiration clearance", etc.
    private Instant timestamp;

    public enum MovementType {
        RESERVE,        // Se reserva cantidad (no sale del disponible aún)
        RELEASE,        // Se libera una reserva (vuelve al disponible)
        CONSUME,        // Se consume una cantidad ya reservada (sale del stock)
        RESTOCK         // Entra nueva cantidad (compra al proveedor)
    }
}
