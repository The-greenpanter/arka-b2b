package co.com.bancolombia.model.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * Aggregate Root del Bounded Context Inventory.
 *
 * Controla la disponibilidad y reservas de unidades de un producto.
 * Mantiene invariantes:
 *  - availableQty >= 0
 *  - reservedQty >= 0
 *  - availableQty + reservedQty = total units en el sistema
 *  - Si availableQty == 0, status = DEPLETED
 *
 * Analogía biológica: el agregado Stock es como el "pool de neurotransmisores"
 * en una sinapsis. El receptor puede reservarlos (binding) y luego consumirlos
 * (liberación). Si se agota, la sinapsis se desactiva (DEPLETED).
 *
 * Reglas:
 *  - Sin dependencias a Spring/JPA/R2DBC. Solo Lombok y JDK.
 *  - Toda mutación pasa por métodos del agregado, no por setters externos.
 *  - Cada método actualiza updatedAt.
 */
@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class Stock {

    private String stockId;         // UUID generado por el use case
    private String productId;       // Referencia al producto en ms-catalog
    private Integer availableQty;   // Cantidad disponible para reservar
    private Integer reservedQty;    // Cantidad ya reservada (no disponible)
    private StockStatus status;     // ACTIVE o DEPLETED
    private Instant createdAt;
    private Instant updatedAt;

    /* ---------- Comportamiento de dominio (state machine) ---------- */

    /**
     * Reserva una cantidad de stock disponible.
     *
     * Precondición: availableQty >= qty
     * Efecto: availableQty -= qty, reservedQty += qty
     * Si availableQty llega a 0, status = DEPLETED
     */
    public Stock reserve(Integer qty) {
        if (qty == null || qty <= 0) {
            throw new IllegalArgumentException("Quantity to reserve must be positive");
        }
        if (this.status == StockStatus.DEPLETED) {
            throw new IllegalStateException("Cannot reserve from DEPLETED stock");
        }
        if (this.availableQty < qty) {
            throw new IllegalStateException(
                    "Insufficient available quantity. Requested: " + qty +
                    ", available: " + this.availableQty);
        }

        this.availableQty -= qty;
        this.reservedQty += qty;

        // Auto-transición si se agota
        if (this.availableQty == 0) {
            this.status = StockStatus.DEPLETED;
        }

        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Libera una cantidad previamente reservada.
     *
     * Precondición: reservedQty >= qty
     * Efecto: reservedQty -= qty, availableQty += qty
     * Si el status era DEPLETED y ahora hay disponible, vuelve a ACTIVE
     */
    public Stock releaseReservation(Integer qty) {
        if (qty == null || qty <= 0) {
            throw new IllegalArgumentException("Quantity to release must be positive");
        }
        if (this.reservedQty < qty) {
            throw new IllegalStateException(
                    "Cannot release more than reserved. Requested: " + qty +
                    ", reserved: " + this.reservedQty);
        }

        this.reservedQty -= qty;
        this.availableQty += qty;

        // Auto-transición desde DEPLETED a ACTIVE si hay disponible
        if (this.status == StockStatus.DEPLETED && this.availableQty > 0) {
            this.status = StockStatus.ACTIVE;
        }

        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Consume una cantidad reservada (la saca del sistema completamente).
     *
     * Precondición: reservedQty >= qty
     * Efecto: reservedQty -= qty (no afecta availableQty)
     */
    public Stock consumeReservation(Integer qty) {
        if (qty == null || qty <= 0) {
            throw new IllegalArgumentException("Quantity to consume must be positive");
        }
        if (this.reservedQty < qty) {
            throw new IllegalStateException(
                    "Cannot consume more than reserved. Requested: " + qty +
                    ", reserved: " + this.reservedQty);
        }

        this.reservedQty -= qty;
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Agrega nueva cantidad al stock disponible (restock desde proveedor).
     *
     * Precondición: qty > 0
     * Efecto: availableQty += qty, status = ACTIVE
     */
    public Stock restock(Integer qty) {
        if (qty == null || qty <= 0) {
            throw new IllegalArgumentException("Restock quantity must be positive");
        }

        this.availableQty += qty;
        this.status = StockStatus.ACTIVE;
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Inicializa el stock con cantidad cero (HU1 saga: cuando ProductValidatedEvent llega).
     *
     * Este es un caso especial: se crea un stock ACTIVE pero con 0 disponible y 0 reservado.
     * En el next restock, se llenará.
     */
    public static Stock createEmpty(String stockId, String productId) {
        return Stock.builder()
                .stockId(stockId)
                .productId(productId)
                .availableQty(0)
                .reservedQty(0)
                .status(StockStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
