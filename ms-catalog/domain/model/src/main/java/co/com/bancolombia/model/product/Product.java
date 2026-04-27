package co.com.bancolombia.model.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Aggregate Root del Bounded Context Catalog.
 *
 * Analogía biológica: el Producto es como una proteína expresada por un gen.
 * El Aggregate Root es la única "puerta" por la que el sistema externo puede
 * provocar cambios en el estado interno (igual que el receptor de membrana es
 * el único punto de entrada controlado al citoplasma).
 *
 * Reglas:
 *  - Sin dependencias a Spring/JPA/Mongo. Solo Lombok y JDK.
 *  - Toda mutación pasa por métodos del agregado, no por setters externos.
 */
@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class Product {

    private String productId;       // UUID generado por el use case
    private String name;
    private String description;
    private String category;
    private ProductStatus status;
    private BigDecimal basePriceUsd;
    private BigDecimal taxRate;
    private Integer minOrderQty;
    private Integer maxOrderQty;
    private Supplier supplier;
    private Map<String, Object> attributes;  // Polimórfico según category
    private Instant createdAt;
    private Instant updatedAt;

    /* ---------- Comportamiento de dominio (state machine) ---------- */

    public Product confirm() {
        if (this.status != ProductStatus.EN_CREACION_STOCK) {
            throw new IllegalStateException(
                    "Solo se puede CONFIRMAR un Product en EN_CREACION_STOCK. Estado actual: " + this.status);
        }
        this.status = ProductStatus.CONFIRMADO;
        this.updatedAt = Instant.now();
        return this;
    }

    public Product reject(String reason) {
        if (this.status == ProductStatus.CONFIRMADO || this.status == ProductStatus.INACTIVO) {
            throw new IllegalStateException("No se puede RECHAZAR un Product ya CONFIRMADO/INACTIVO");
        }
        this.status = ProductStatus.RECHAZADO;
        this.updatedAt = Instant.now();
        return this;
    }

    public Product validateProvider() {
        if (this.status != ProductStatus.EN_CREACION) {
            throw new IllegalStateException("Transición inválida desde " + this.status);
        }
        this.status = ProductStatus.VALIDANDO_PROVEEDOR;
        this.updatedAt = Instant.now();
        return this;
    }

    public Product moveToCreatingStock() {
        if (this.status != ProductStatus.VALIDANDO_PROVEEDOR) {
            throw new IllegalStateException("Transición inválida desde " + this.status);
        }
        this.status = ProductStatus.EN_CREACION_STOCK;
        this.updatedAt = Instant.now();
        return this;
    }

    public Product deactivate() {
        if (this.status != ProductStatus.CONFIRMADO) {
            throw new IllegalStateException("Solo se puede desactivar un Product CONFIRMADO");
        }
        this.status = ProductStatus.INACTIVO;
        this.updatedAt = Instant.now();
        return this;
    }
}
