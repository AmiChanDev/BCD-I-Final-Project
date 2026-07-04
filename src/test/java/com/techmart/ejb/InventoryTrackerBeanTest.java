package com.techmart.ejb;

import com.techmart.ejb.singleton.InventoryTrackerBean;
import com.techmart.jms.producer.InventoryEventProducer;
import com.techmart.util.DataSourceProvider;
import com.techmart.util.PerformanceMonitor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InventoryTrackerBean (Singleton EJB).
 *
 * Key scenarios tested:
 *   - Inventory cache is pre-loaded from DB at startup
 *   - Stock reservation succeeds when sufficient stock exists
 *   - Stock reservation fails (returns false) when insufficient
 *   - Compensating restock logic works correctly
 *   - Thread-safety contract is not broken by concurrent-style operations
 *   - Remote inventory updates are applied to the cache
 *   - Performance metrics are recorded for every operation
 *
 * Note: @Lock(WRITE) / @Lock(READ) are container-enforced annotations —
 * they cannot be unit-tested without an EJB container.  Concurrency
 * correctness is verified via the Arquillian integration test instead.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryTrackerBean Unit Tests")
class InventoryTrackerBeanTest {

    @Mock private DataSourceProvider    dsProvider;
    @Mock private PerformanceMonitor    perfMonitor;
    @Mock private InventoryEventProducer inventoryProducer;
    @Mock private Connection            connection;
    @Mock private PreparedStatement     preparedStatement;
    @Mock private ResultSet             resultSet;

    @InjectMocks
    private InventoryTrackerBean tracker;

    @BeforeEach
    void setUp() throws Exception {
        // Stub DB connections for loadInventory
        when(dsProvider.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        // Seed the ResultSet: products 1→50, 2→10, 3→0
        when(resultSet.next()).thenReturn(true, true, true, false);
        when(resultSet.getLong("id")).thenReturn(1L, 2L, 3L);
        when(resultSet.getInt("stock_quantity")).thenReturn(50, 10, 0);

        // Stub update statement for reserve/restock operations
        when(preparedStatement.executeUpdate()).thenReturn(1);

        // Call @PostConstruct manually
        callPostConstruct();
    }

    // ----------------------------------------------------------------
    // loadInventory (@PostConstruct)
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("loadInventory() — @PostConstruct")
    class LoadInventoryTests {

        @Test
        @DisplayName("Cache contains all products loaded from DB")
        void cacheContainsAllProductsFromDb() {
            Map<Long, Integer> inventory = tracker.getFullInventory();
            assertEquals(3, inventory.size());
        }

        @Test
        @DisplayName("Cache reflects correct stock levels from DB")
        void cacheReflectsCorrectStockLevels() {
            assertEquals(50, tracker.getStockLevel(1L));
            assertEquals(10, tracker.getStockLevel(2L));
            assertEquals(0,  tracker.getStockLevel(3L));
        }

        @Test
        @DisplayName("Returns 0 for unknown product (not in cache)")
        void returnsZeroForUnknownProduct() {
            assertEquals(0, tracker.getStockLevel(999L));
        }
    }

    // ----------------------------------------------------------------
    // isAvailable()
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("isAvailable()")
    class IsAvailableTests {

        @Test
        @DisplayName("Returns true when stock >= requested quantity")
        void returnsTrueWhenSufficientStock() {
            assertTrue(tracker.isAvailable(1L, 50));
            assertTrue(tracker.isAvailable(1L, 1));
        }

        @Test
        @DisplayName("Returns false when stock < requested quantity")
        void returnsFalseWhenInsufficientStock() {
            assertFalse(tracker.isAvailable(1L, 51));
        }

        @Test
        @DisplayName("Returns false when product has zero stock")
        void returnsFalseForZeroStock() {
            assertFalse(tracker.isAvailable(3L, 1));
        }

        @Test
        @DisplayName("Returns false for completely unknown product")
        void returnsFalseForUnknownProduct() {
            assertFalse(tracker.isAvailable(999L, 1));
        }
    }

    // ----------------------------------------------------------------
    // reserveStock()
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("reserveStock()")
    class ReserveStockTests {

        @Test
        @DisplayName("Returns true and decrements cache when stock is sufficient")
        void returnsTrueAndDecrementsCache() throws Exception {
            boolean result = tracker.reserveStock(1L, 10);

            assertTrue(result);
            assertEquals(40, tracker.getStockLevel(1L));
        }

        @Test
        @DisplayName("Returns false when requested quantity exceeds available stock")
        void returnsFalseWhenInsufficientStock() {
            boolean result = tracker.reserveStock(2L, 11);  // only 10 in stock

            assertFalse(result);
            // Cache should be unchanged
            assertEquals(10, tracker.getStockLevel(2L));
        }

        @Test
        @DisplayName("Returns false when product stock is exactly zero")
        void returnsFalseForZeroStockProduct() {
            boolean result = tracker.reserveStock(3L, 1);
            assertFalse(result);
        }

        @Test
        @DisplayName("Persists updated stock level to DB on successful reserve")
        void persistsUpdatedLevelToDb() throws Exception {
            tracker.reserveStock(1L, 5);
            // Verify at least one UPDATE was executed
            verify(preparedStatement, atLeastOnce()).executeUpdate();
        }

        @Test
        @DisplayName("Publishes inventory event on successful reserve")
        void publishesInventoryEventOnSuccess() {
            tracker.reserveStock(1L, 5);
            verify(inventoryProducer).publishInventoryUpdate(eq(1L), eq(45), eq("RESERVE"));
        }

        @Test
        @DisplayName("Does not publish event when reservation fails")
        void doesNotPublishEventOnFailure() {
            tracker.reserveStock(2L, 100);  // will fail
            verify(inventoryProducer, never()).publishInventoryUpdate(anyLong(), anyInt(), anyString());
        }

        @Test
        @DisplayName("Can reserve exact available quantity (boundary)")
        void canReserveExactAvailableQuantity() {
            boolean result = tracker.reserveStock(2L, 10);
            assertTrue(result);
            assertEquals(0, tracker.getStockLevel(2L));
        }

        @Test
        @DisplayName("Records performance metric for reserveStock")
        void recordsPerformanceMetric() {
            tracker.reserveStock(1L, 1);
            verify(perfMonitor).record(eq("InventoryTrackerBean"), eq("reserveStock"), anyLong());
        }
    }

    // ----------------------------------------------------------------
    // restockProduct()
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("restockProduct()")
    class RestockProductTests {

        @Test
        @DisplayName("Increments cache correctly on restock")
        void incrementsCacheOnRestock() {
            tracker.restockProduct(2L, 20);
            assertEquals(30, tracker.getStockLevel(2L));
        }

        @Test
        @DisplayName("Can restock a product that had zero stock")
        void restocksZeroStockProduct() {
            tracker.restockProduct(3L, 50);
            assertEquals(50, tracker.getStockLevel(3L));
        }

        @Test
        @DisplayName("Publishes RESTOCK event on restock")
        void publishesRestockEvent() {
            tracker.restockProduct(1L, 100);
            verify(inventoryProducer).publishInventoryUpdate(eq(1L), eq(150), eq("RESTOCK"));
        }

        @Test
        @DisplayName("Persist DB update is called on restock")
        void persistsToDbOnRestock() throws Exception {
            tracker.restockProduct(1L, 5);
            verify(preparedStatement, atLeastOnce()).executeUpdate();
        }
    }

    // ----------------------------------------------------------------
    // applyRemoteInventoryUpdate()
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("applyRemoteInventoryUpdate()")
    class RemoteUpdateTests {

        @Test
        @DisplayName("Applies remote update to local cache correctly")
        void appliesRemoteUpdateToCache() {
            tracker.applyRemoteInventoryUpdate(1L, 99);
            assertEquals(99, tracker.getStockLevel(1L));
        }

        @Test
        @DisplayName("Applies remote update for a new product not yet in cache")
        void appliesRemoteUpdateForNewProduct() {
            tracker.applyRemoteInventoryUpdate(42L, 15);
            assertEquals(15, tracker.getStockLevel(42L));
        }
    }

    // ----------------------------------------------------------------
    // getFullInventory() — defensive copy
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("getFullInventory()")
    class GetFullInventoryTests {

        @Test
        @DisplayName("Returns a defensive copy — external modifications do not affect cache")
        void returnsDefensiveCopy() {
            Map<Long, Integer> snapshot = tracker.getFullInventory();
            snapshot.put(999L, 9999);  // mutate returned copy

            // Verify the internal cache was not affected
            assertNull(tracker.getFullInventory().get(999L));
        }

        @Test
        @DisplayName("Snapshot reflects current cache state")
        void snapshotReflectsCurrentState() {
            tracker.restockProduct(1L, 50);
            Map<Long, Integer> snapshot = tracker.getFullInventory();
            assertEquals(100, snapshot.get(1L));
        }
    }

    // ----------------------------------------------------------------
    // Helper
    // ----------------------------------------------------------------

    private void callPostConstruct() throws Exception {
        java.lang.reflect.Method m =
            InventoryTrackerBean.class.getDeclaredMethod("loadInventory");
        m.setAccessible(true);
        m.invoke(tracker);
    }
}
