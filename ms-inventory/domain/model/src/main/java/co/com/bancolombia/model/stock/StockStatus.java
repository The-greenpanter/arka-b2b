package co.com.bancolombia.model.stock;

/**
 * Estados del agregado Stock.
 *
 * ACTIVE    → El stock es operacional, pueden hacerse reservas y consumos.
 * DEPLETED  → El stock llegó a cero, no se pueden hacer más reservas hasta restock.
 *
 * La transición ocurre automáticamente cuando availableQty llega a 0.
 */
public enum StockStatus {
    ACTIVE,
    DEPLETED
}
