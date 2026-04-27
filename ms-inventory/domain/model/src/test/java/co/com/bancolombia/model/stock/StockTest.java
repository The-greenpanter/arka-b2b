package co.com.bancolombia.model.stock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de la state machine del agregado Stock.
 *
 * Cada operación de reserva, liberación, consumo y restock tiene tests
 * positivos (happy path) y negativos (guardrails).
 *
 * Estos tests NO usan Spring, ni Mockito, ni Reactor.
 * Son tests de lógica pura de dominio — los más rápidos y valiosos.
 */
class StockTest {

    private Stock stock;

    @BeforeEach
    void setUp() {
        stock = Stock.builder()
                .stockId("stock-001")
                .productId("prod-001")
                .availableQty(100)
                .reservedQty(0)
                .status(StockStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("Reserva de stock")
    class ReserveOperations {

        @Test
        @DisplayName("reserve() reduce availableQty y aumenta reservedQty")
        void shouldReserveSuccessfully() {
            stock.reserve(30);

            assertEquals(70, stock.getAvailableQty());
            assertEquals(30, stock.getReservedQty());
            assertEquals(StockStatus.ACTIVE, stock.getStatus());
            assertNotNull(stock.getUpdatedAt());
        }

        @Test
        @DisplayName("reserve() múltiples veces acumula cantidad reservada")
        void shouldReserveMultipleTimes() {
            stock.reserve(20).reserve(15).reserve(10);

            assertEquals(55, stock.getAvailableQty());
            assertEquals(45, stock.getReservedQty());
        }

        @Test
        @DisplayName("reserve() que agota stock transiciona a DEPLETED")
        void shouldTransitionToDepletedWhenExhausted() {
            stock.reserve(100);

            assertEquals(0, stock.getAvailableQty());
            assertEquals(100, stock.getReservedQty());
            assertEquals(StockStatus.DEPLETED, stock.getStatus());
        }

        @Test
        @DisplayName("reserve() con qty > availableQty lanza excepción")
        void shouldFailWhenInsufficientAvailable() {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> stock.reserve(150));
            assertTrue(ex.getMessage().contains("Insufficient"));
        }

        @Test
        @DisplayName("reserve() desde DEPLETED lanza excepción")
        void shouldFailFromDepleted() {
            stock.reserve(100);  // agota
            assertEquals(StockStatus.DEPLETED, stock.getStatus());

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> stock.reserve(1));
            assertTrue(ex.getMessage().contains("DEPLETED"));
        }

        @Test
        @DisplayName("reserve(null) o reserve(0) lanza excepción")
        void shouldFailWithInvalidQuantity() {
            assertThrows(IllegalArgumentException.class, () -> stock.reserve(null));
            assertThrows(IllegalArgumentException.class, () -> stock.reserve(0));
            assertThrows(IllegalArgumentException.class, () -> stock.reserve(-5));
        }
    }

    @Nested
    @DisplayName("Liberación de reserva")
    class ReleaseOperations {

        @BeforeEach
        void setUpReserved() {
            stock.reserve(50);
        }

        @Test
        @DisplayName("releaseReservation() disminuye reservedQty e incrementa availableQty")
        void shouldReleaseSuccessfully() {
            stock.releaseReservation(20);

            assertEquals(70, stock.getAvailableQty());   // 50 (after reserve) + 20 (released)
            assertEquals(30, stock.getReservedQty());   // 50 (reserved) - 20 (released)
            assertEquals(StockStatus.ACTIVE, stock.getStatus());
        }

        @Test
        @DisplayName("releaseReservation() desde DEPLETED vuelve a ACTIVE")
        void shouldTransitionFromDepletedToActive() {
            stock.reserve(50);  // Ahora: available=0, reserved=100, DEPLETED
            assertEquals(StockStatus.DEPLETED, stock.getStatus());

            stock.releaseReservation(50);

            assertEquals(50, stock.getAvailableQty());
            assertEquals(50, stock.getReservedQty());
            assertEquals(StockStatus.ACTIVE, stock.getStatus());
        }

        @Test
        @DisplayName("releaseReservation(qty) > reservedQty lanza excepción")
        void shouldFailWhenExceeding() {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> stock.releaseReservation(100));
            assertTrue(ex.getMessage().contains("Cannot release"));
        }

        @Test
        @DisplayName("releaseReservation(null) o releaseReservation(0) lanza excepción")
        void shouldFailWithInvalidQuantity() {
            assertThrows(IllegalArgumentException.class, () -> stock.releaseReservation(null));
            assertThrows(IllegalArgumentException.class, () -> stock.releaseReservation(0));
        }
    }

    @Nested
    @DisplayName("Consumo de reserva")
    class ConsumeOperations {

        @BeforeEach
        void setUpReserved() {
            stock.reserve(50);
        }

        @Test
        @DisplayName("consumeReservation() disminuye solo reservedQty")
        void shouldConsumeSuccessfully() {
            stock.consumeReservation(30);

            assertEquals(50, stock.getAvailableQty());  // sin cambios
            assertEquals(20, stock.getReservedQty());   // reducido
            assertEquals(StockStatus.ACTIVE, stock.getStatus());
        }

        @Test
        @DisplayName("consumeReservation() múltiples veces es acumulativo")
        void shouldConsumeMultipleTimes() {
            stock.consumeReservation(10).consumeReservation(15).consumeReservation(25);

            assertEquals(50, stock.getAvailableQty());
            assertEquals(0, stock.getReservedQty());
        }

        @Test
        @DisplayName("consumeReservation(qty) > reservedQty lanza excepción")
        void shouldFailWhenExceeding() {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> stock.consumeReservation(100));
            assertTrue(ex.getMessage().contains("Cannot consume"));
        }
    }

    @Nested
    @DisplayName("Restock (entrada de nueva cantidad)")
    class RestockOperations {

        @Test
        @DisplayName("restock() incrementa availableQty y asegura ACTIVE")
        void shouldRestockSuccessfully() {
            stock.restock(50);

            assertEquals(150, stock.getAvailableQty());
            assertEquals(0, stock.getReservedQty());
            assertEquals(StockStatus.ACTIVE, stock.getStatus());
        }

        @Test
        @DisplayName("restock() en DEPLETED vuelve a ACTIVE")
        void shouldTransitionFromDepletedToActive() {
            stock.reserve(100);  // agota
            assertEquals(StockStatus.DEPLETED, stock.getStatus());

            stock.restock(75);

            assertEquals(75, stock.getAvailableQty());
            assertEquals(100, stock.getReservedQty());
            assertEquals(StockStatus.ACTIVE, stock.getStatus());
        }

        @Test
        @DisplayName("restock(null) o restock(0) lanza excepción")
        void shouldFailWithInvalidQuantity() {
            assertThrows(IllegalArgumentException.class, () -> stock.restock(null));
            assertThrows(IllegalArgumentException.class, () -> stock.restock(0));
            assertThrows(IllegalArgumentException.class, () -> stock.restock(-10));
        }
    }

    @Nested
    @DisplayName("Flujo completo: reservar, consumir, liberar, restock")
    class ComplexScenarios {

        @Test
        @DisplayName("Flujo: reservar 30, consumir 20, liberar 10, entonces: avail=100, reserved=0")
        void shouldHandleComplexFlow() {
            // Inicial: 100 disponible, 0 reservado
            stock.reserve(30);      // Ahora: 70 disponible, 30 reservado
            assertEquals(70, stock.getAvailableQty());
            assertEquals(30, stock.getReservedQty());

            stock.consumeReservation(20); // Ahora: 70 disponible, 10 reservado
            assertEquals(70, stock.getAvailableQty());
            assertEquals(10, stock.getReservedQty());

            stock.releaseReservation(10); // Ahora: 80 disponible, 0 reservado
            assertEquals(80, stock.getAvailableQty());
            assertEquals(0, stock.getReservedQty());
        }

        @Test
        @DisplayName("Reservar hasta DEPLETED, restock, reservar de nuevo")
        void shouldHandleDepletionAndRecovery() {
            // Agotar
            stock.reserve(100);
            assertEquals(StockStatus.DEPLETED, stock.getStatus());

            // Restock
            stock.restock(200);
            assertEquals(200, stock.getAvailableQty());
            assertEquals(StockStatus.ACTIVE, stock.getStatus());

            // Reservar de nuevo
            stock.reserve(150);
            assertEquals(50, stock.getAvailableQty());
            assertEquals(250, stock.getReservedQty()); // 100 (previas) + 150 (nuevas)
        }
    }

    @Nested
    @DisplayName("Factory: createEmpty")
    class FactoryOperations {

        @Test
        @DisplayName("createEmpty() genera stock con 0 cantidad pero ACTIVE")
        void shouldCreateEmptyStock() {
            Stock empty = Stock.createEmpty("stock-002", "prod-002");

            assertEquals("stock-002", empty.getStockId());
            assertEquals("prod-002", empty.getProductId());
            assertEquals(0, empty.getAvailableQty());
            assertEquals(0, empty.getReservedQty());
            assertEquals(StockStatus.ACTIVE, empty.getStatus());
            assertNotNull(empty.getCreatedAt());
            assertNotNull(empty.getUpdatedAt());
        }
    }

    @Nested
    @DisplayName("Invariantes: updatedAt se actualiza en cada operación")
    class TimestampInvariants {

        @Test
        @DisplayName("Cada operación actualiza updatedAt")
        void shouldUpdateTimestampOnEachOperation() {
            Instant before = stock.getUpdatedAt();

            stock.reserve(10);
            Instant after1 = stock.getUpdatedAt();
            assertTrue(after1.compareTo(before) >= 0);

            stock.releaseReservation(5);
            Instant after2 = stock.getUpdatedAt();
            assertTrue(after2.compareTo(after1) >= 0);

            stock.consumeReservation(5);
            Instant after3 = stock.getUpdatedAt();
            assertTrue(after3.compareTo(after2) >= 0);

            stock.restock(50);
            Instant after4 = stock.getUpdatedAt();
            assertTrue(after4.compareTo(after3) >= 0);
        }
    }
}
