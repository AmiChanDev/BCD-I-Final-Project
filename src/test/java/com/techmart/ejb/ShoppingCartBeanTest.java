package com.techmart.ejb;

import com.techmart.ejb.stateful.ShoppingCartBean;
import com.techmart.model.CartItem;
import com.techmart.model.Order;
import com.techmart.util.PerformanceMonitor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ShoppingCartBean (Stateful EJB).
 *
 * Because the cart is a POJO from the test's perspective (no container),
 * we instantiate it directly and inject the PerformanceMonitor mock via
 * Mockito @InjectMocks.
 *
 * Tests verify the core cart lifecycle:
 *   add → update → remove → buildOrder → checkout
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShoppingCartBean Unit Tests")
class ShoppingCartBeanTest {

    @Mock
    private PerformanceMonitor perfMonitor;

    @InjectMocks
    private ShoppingCartBean cart;

    @BeforeEach
    void setUp() throws Exception {
        // Simulate @PostConstruct (not called outside container)
        callPostConstruct();
        cart.setCustomerId("customer-001");
    }

    // ----------------------------------------------------------------
    // Initial state
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("Initial state")
    class InitialStateTests {

        @Test
        @DisplayName("Cart is empty after initialisation")
        void cartIsInitiallyEmpty() {
            assertTrue(cart.isEmpty());
        }

        @Test
        @DisplayName("Item count is zero after initialisation")
        void itemCountIsZeroInitially() {
            assertEquals(0, cart.getItemCount());
        }

        @Test
        @DisplayName("Total is zero after initialisation")
        void totalIsZeroInitially() {
            assertEquals(BigDecimal.ZERO, cart.getTotal());
        }
    }

    // ----------------------------------------------------------------
    // addItem()
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("addItem()")
    class AddItemTests {

        @Test
        @DisplayName("Adds a new item to an empty cart")
        void addsNewItemToEmptyCart() {
            cart.addItem(1L, "LAPTOP-001", "Laptop Pro", 1, new BigDecimal("999.99"));

            assertFalse(cart.isEmpty());
            assertEquals(1, cart.getItemCount());
        }

        @Test
        @DisplayName("Increments quantity when same product added twice")
        void incrementsQuantityForDuplicateProduct() {
            cart.addItem(1L, "LAPTOP-001", "Laptop Pro", 1, new BigDecimal("999.99"));
            cart.addItem(1L, "LAPTOP-001", "Laptop Pro", 2, new BigDecimal("999.99"));

            // Should still be 1 distinct item, but quantity = 3
            List<CartItem> items = cart.getItems();
            assertEquals(1, items.size());
            assertEquals(3, items.get(0).getQuantity());
        }

        @Test
        @DisplayName("Adds two distinct products as separate items")
        void addsDistinctProductsAsSeparateItems() {
            cart.addItem(1L, "LAPTOP-001", "Laptop Pro",  1, new BigDecimal("999.99"));
            cart.addItem(2L, "PHONE-001",  "Smartphone X", 2, new BigDecimal("499.99"));

            assertEquals(3, cart.getItemCount());  // 1 laptop + 2 phones
            assertEquals(2, cart.getItems().size()); // 2 distinct lines
        }

        @Test
        @DisplayName("Calculates correct total after adding items")
        void calculatesCorrectTotal() {
            cart.addItem(1L, "LAPTOP-001", "Laptop Pro",   1, new BigDecimal("1000.00"));
            cart.addItem(2L, "PHONE-001",  "Smartphone X", 2, new BigDecimal("500.00"));

            // 1×1000 + 2×500 = 2000
            assertEquals(new BigDecimal("2000.00"), cart.getTotal());
        }

        @Test
        @DisplayName("Records performance metric for addItem")
        void recordsPerformanceMetric() {
            cart.addItem(1L, "SKU-001", "Product", 1, new BigDecimal("10.00"));
            verify(perfMonitor).record(eq("ShoppingCartBean"), eq("addItem"), anyLong());
        }
    }

    // ----------------------------------------------------------------
    // removeItem()
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("removeItem()")
    class RemoveItemTests {

        @Test
        @DisplayName("Removes an existing item from cart")
        void removesExistingItem() {
            cart.addItem(1L, "LAPTOP-001", "Laptop Pro", 1, new BigDecimal("999.99"));
            cart.addItem(2L, "PHONE-001", "Phone", 1, new BigDecimal("399.99"));

            cart.removeItem(1L);

            assertEquals(1, cart.getItems().size());
            assertEquals("PHONE-001", cart.getItems().get(0).getSku());
        }

        @Test
        @DisplayName("Removing non-existent item does not throw")
        void removingNonExistentItemIsNoOp() {
            assertDoesNotThrow(() -> cart.removeItem(999L));
        }

        @Test
        @DisplayName("Cart is empty after removing the only item")
        void cartEmptyAfterRemovingOnlyItem() {
            cart.addItem(1L, "SKU-001", "Product", 1, new BigDecimal("10.00"));
            cart.removeItem(1L);
            assertTrue(cart.isEmpty());
        }

        @Test
        @DisplayName("Total recalculates correctly after removal")
        void totalRecalculatesAfterRemoval() {
            cart.addItem(1L, "LAPTOP-001", "Laptop", 1, new BigDecimal("1000.00"));
            cart.addItem(2L, "PHONE-001",  "Phone",  1, new BigDecimal("400.00"));

            cart.removeItem(1L);

            assertEquals(new BigDecimal("400.00"), cart.getTotal());
        }
    }

    // ----------------------------------------------------------------
    // updateQuantity()
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("updateQuantity()")
    class UpdateQuantityTests {

        @Test
        @DisplayName("Updates quantity for an existing item")
        void updatesQuantityForExistingItem() {
            cart.addItem(1L, "SKU-001", "Product", 2, new BigDecimal("50.00"));
            cart.updateQuantity(1L, 5);

            assertEquals(5, cart.getItems().get(0).getQuantity());
        }

        @Test
        @DisplayName("Removes item when quantity set to zero")
        void removesItemWhenQuantityIsZero() {
            cart.addItem(1L, "SKU-001", "Product", 2, new BigDecimal("50.00"));
            cart.updateQuantity(1L, 0);

            assertTrue(cart.isEmpty());
        }

        @Test
        @DisplayName("Removes item when quantity is negative")
        void removesItemWhenQuantityIsNegative() {
            cart.addItem(1L, "SKU-001", "Product", 2, new BigDecimal("50.00"));
            cart.updateQuantity(1L, -1);

            assertTrue(cart.isEmpty());
        }

        @Test
        @DisplayName("No-op when updating quantity for non-existent product")
        void noOpForNonExistentProduct() {
            assertDoesNotThrow(() -> cart.updateQuantity(999L, 3));
        }
    }

    // ----------------------------------------------------------------
    // buildOrder()
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("buildOrder()")
    class BuildOrderTests {

        @Test
        @DisplayName("Builds Order with correct customer and address")
        void buildsOrderWithCorrectCustomerInfo() {
            cart.addItem(1L, "LAPTOP-001", "Laptop Pro", 1, new BigDecimal("999.99"));

            Order order = cart.buildOrder("test@techmart.com", "123 Main St");

            assertEquals("customer-001", order.getCustomerId());
            assertEquals("test@techmart.com", order.getCustomerEmail());
            assertEquals("123 Main St", order.getShippingAddress());
        }

        @Test
        @DisplayName("Order contains correct number of line items")
        void orderContainsCorrectLineItems() {
            cart.addItem(1L, "LAPTOP-001", "Laptop", 1, new BigDecimal("1000.00"));
            cart.addItem(2L, "PHONE-001",  "Phone",  2, new BigDecimal("500.00"));

            Order order = cart.buildOrder("test@techmart.com", "123 Main St");

            assertEquals(2, order.getItems().size());
        }

        @Test
        @DisplayName("Order total matches cart total")
        void orderTotalMatchesCartTotal() {
            cart.addItem(1L, "LAPTOP-001", "Laptop", 1, new BigDecimal("1000.00"));
            cart.addItem(2L, "PHONE-001",  "Phone",  2, new BigDecimal("500.00"));

            BigDecimal cartTotal = cart.getTotal();
            Order order = cart.buildOrder("test@techmart.com", "123 Main St");

            assertEquals(cartTotal, order.getTotalAmount());
        }

        @Test
        @DisplayName("Throws IllegalStateException when cart is empty")
        void throwsExceptionWhenCartIsEmpty() {
            assertThrows(IllegalStateException.class,
                () -> cart.buildOrder("test@techmart.com", "123 Main St"));
        }

        @Test
        @DisplayName("Order status is PENDING after build")
        void orderStatusIsPendingAfterBuild() {
            cart.addItem(1L, "SKU-001", "Product", 1, new BigDecimal("100.00"));
            Order order = cart.buildOrder("test@techmart.com", "123 Main St");
            assertEquals(Order.Status.PENDING, order.getStatus());
        }
    }

    // ----------------------------------------------------------------
    // getItems() immutability
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("getItems() immutability")
    class GetItemsImmutabilityTest {

        @Test
        @DisplayName("getItems() returns unmodifiable view")
        void getItemsReturnsUnmodifiableView() {
            cart.addItem(1L, "SKU-001", "Product", 1, new BigDecimal("10.00"));

            List<CartItem> items = cart.getItems();
            assertThrows(UnsupportedOperationException.class,
                () -> items.remove(0));
        }
    }

    // ----------------------------------------------------------------
    // Helper: invoke @PostConstruct via reflection
    // ----------------------------------------------------------------

    private void callPostConstruct() throws Exception {
        java.lang.reflect.Method init =
            ShoppingCartBean.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(cart);
    }
}
