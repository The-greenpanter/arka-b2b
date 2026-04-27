package co.com.bancolombia.model.product;

/**
 * Estados del agregado Product (state machine de HU1).
 *
 *   EN_CREACION → VALIDANDO_PROVEEDOR → EN_CREACION_STOCK → CONFIRMADO ⇄ INACTIVO
 *                                ↘ RECHAZADO
 *
 * Análogo biológico: el ciclo de vida de un orgánulo recién sintetizado que
 * pasa por checkpoints (validación de plegamiento, transporte al destino,
 * confirmación funcional) antes de quedar activo en la célula.
 */
public enum ProductStatus {
    EN_CREACION,
    VALIDANDO_PROVEEDOR,
    EN_CREACION_STOCK,
    CONFIRMADO,
    RECHAZADO,
    INACTIVO
}
