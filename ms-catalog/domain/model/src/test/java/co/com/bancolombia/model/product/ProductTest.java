package co.com.bancolombia.model.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de la state machine del agregado Product.
 *
 * Cada transición válida tiene un test positivo, y cada transición
 * inválida tiene un test que verifica IllegalStateException.
 *
 * Estos tests NO usan Spring, ni Mockito, ni Reactor.
 * Son tests de lógica pura de dominio — los más rápidos y valiosos.
 */
class ProductTest {

    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .productId("prod-001")
                .name("Teclado Mecánico")
                .category("TECLADOS")
                .basePriceUsd(BigDecimal.valueOf(129.99))
                .status(ProductStatus.EN_CREACION)
                .supplier(Supplier.builder()
                        .supplierId("sup-001")
                        .name("TechDistribuidora")
                        .leadTimeDays(7)
                        .build())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("Happy path: EN_CREACION → CONFIRMADO")
    class HappyPath {

        @Test
        @DisplayName("validateProvider() cambia a VALIDANDO_PROVEEDOR")
        void shouldTransitionToValidandoProveedor() {
            product.validateProvider();
            assertEquals(ProductStatus.VALIDANDO_PROVEEDOR, product.getStatus());
            assertNotNull(product.getUpdatedAt());
        }

        @Test
        @DisplayName("moveToCreatingStock() cambia a EN_CREACION_STOCK")
        void shouldTransitionToEnCreacionStock() {
            product.validateProvider();
            product.moveToCreatingStock();
            assertEquals(ProductStatus.EN_CREACION_STOCK, product.getStatus());
        }

        @Test
        @DisplayName("confirm() cambia a CONFIRMADO")
        void shouldTransitionToConfirmado() {
            product.validateProvider();
            product.moveToCreatingStock();
            product.confirm();
            assertEquals(ProductStatus.CONFIRMADO, product.getStatus());
        }

        @Test
        @DisplayName("Cadena completa: EN_CREACION → CONFIRMADO")
        void shouldCompleteFullHappyPath() {
            product.validateProvider()
                    .moveToCreatingStock()
                    .confirm();
            assertEquals(ProductStatus.CONFIRMADO, product.getStatus());
        }
    }

    @Nested
    @DisplayName("Rechazo de proveedor")
    class ProviderRejection {

        @Test
        @DisplayName("reject() desde EN_CREACION → RECHAZADO")
        void shouldRejectFromEnCreacion() {
            product.reject("Proveedor no encontrado");
            assertEquals(ProductStatus.RECHAZADO, product.getStatus());
        }

        @Test
        @DisplayName("reject() desde VALIDANDO_PROVEEDOR → RECHAZADO")
        void shouldRejectFromValidandoProveedor() {
            product.validateProvider();
            product.reject("Proveedor inválido");
            assertEquals(ProductStatus.RECHAZADO, product.getStatus());
        }

        @Test
        @DisplayName("reject() desde EN_CREACION_STOCK → RECHAZADO")
        void shouldRejectFromEnCreacionStock() {
            product.validateProvider().moveToCreatingStock();
            product.reject("Inventario falló");
            assertEquals(ProductStatus.RECHAZADO, product.getStatus());
        }
    }

    @Nested
    @DisplayName("Desactivación de producto confirmado")
    class Deactivation {

        @Test
        @DisplayName("deactivate() desde CONFIRMADO → INACTIVO")
        void shouldDeactivateConfirmedProduct() {
            product.validateProvider().moveToCreatingStock().confirm();
            product.deactivate();
            assertEquals(ProductStatus.INACTIVO, product.getStatus());
        }

        @Test
        @DisplayName("deactivate() desde EN_CREACION lanza excepción")
        void shouldFailDeactivateFromEnCreacion() {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class, () -> product.deactivate());
            assertTrue(ex.getMessage().contains("CONFIRMADO"));
        }
    }

    @Nested
    @DisplayName("Transiciones inválidas (guardrails de la state machine)")
    class InvalidTransitions {

        @Test
        @DisplayName("confirm() desde EN_CREACION lanza excepción")
        void shouldFailConfirmFromEnCreacion() {
            assertThrows(IllegalStateException.class, () -> product.confirm());
        }

        @Test
        @DisplayName("confirm() desde VALIDANDO_PROVEEDOR lanza excepción")
        void shouldFailConfirmFromValidandoProveedor() {
            product.validateProvider();
            assertThrows(IllegalStateException.class, () -> product.confirm());
        }

        @Test
        @DisplayName("moveToCreatingStock() desde EN_CREACION lanza excepción")
        void shouldFailMoveToCreatingStockFromEnCreacion() {
            assertThrows(IllegalStateException.class, () -> product.moveToCreatingStock());
        }

        @Test
        @DisplayName("validateProvider() desde CONFIRMADO lanza excepción")
        void shouldFailValidateProviderFromConfirmado() {
            product.validateProvider().moveToCreatingStock().confirm();
            assertThrows(IllegalStateException.class, () -> product.validateProvider());
        }

        @Test
        @DisplayName("reject() desde CONFIRMADO lanza excepción")
        void shouldFailRejectFromConfirmado() {
            product.validateProvider().moveToCreatingStock().confirm();
            assertThrows(IllegalStateException.class,
                    () -> product.reject("no debería poder"));
        }

        @Test
        @DisplayName("reject() desde INACTIVO lanza excepción")
        void shouldFailRejectFromInactivo() {
            product.validateProvider().moveToCreatingStock().confirm().deactivate();
            assertThrows(IllegalStateException.class,
                    () -> product.reject("no debería poder"));
        }
    }

    @Nested
    @DisplayName("updatedAt se actualiza en cada transición")
    class TimestampUpdates {

        @Test
        @DisplayName("Cada transición actualiza updatedAt")
        void shouldUpdateTimestampOnEachTransition() {
            Instant before = product.getUpdatedAt();

            product.validateProvider();
            Instant after1 = product.getUpdatedAt();
            assertTrue(after1.compareTo(before) >= 0);

            product.moveToCreatingStock();
            Instant after2 = product.getUpdatedAt();
            assertTrue(after2.compareTo(after1) >= 0);

            product.confirm();
            Instant after3 = product.getUpdatedAt();
            assertTrue(after3.compareTo(after2) >= 0);
        }
    }
}
