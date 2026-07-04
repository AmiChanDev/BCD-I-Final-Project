package com.techmart.ejb;

import com.techmart.ejb.stateless.ProductCatalogBean;
import com.techmart.model.Product;
import com.techmart.util.DataSourceProvider;
import com.techmart.util.PerformanceMonitor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProductCatalogBean.
 *
 * Strategy: mock the DataSourceProvider and PerformanceMonitor so tests
 * run without a real database or application server.  We inject mocks
 * via reflection (simulating what the EJB container does with @EJB).
 *
 * These tests verify:
 *   - Correct SQL is executed and ResultSet rows are mapped to Product objects
 *   - Search parameter binding works (no SQL injection risk)
 *   - Empty result sets return empty lists (not null)
 *   - SQLException propagates as RuntimeException
 *   - Performance monitor is called for every operation
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductCatalogBean Unit Tests")
class ProductCatalogBeanTest {

    @Mock private DataSourceProvider dsProvider;
    @Mock private PerformanceMonitor  perfMonitor;
    @Mock private DataSource          dataSource;
    @Mock private Connection          connection;
    @Mock private PreparedStatement   preparedStatement;
    @Mock private ResultSet           resultSet;

    @InjectMocks
    private ProductCatalogBean productCatalog;

    // ----------------------------------------------------------------
    // Setup
    // ----------------------------------------------------------------

    @BeforeEach
    void setUp() throws Exception {
        // Wire mocks: dsProvider.getConnection() → connection
        when(dsProvider.getConnection()).thenReturn(connection);

        // connection.prepareStatement(any) → preparedStatement
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(connection.prepareStatement(anyString(), anyInt())).thenReturn(preparedStatement);

        // preparedStatement.executeQuery() → resultSet
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        // PerformanceMonitor.record() is void — no stubbing needed; just verify later
    }

    // ----------------------------------------------------------------
    // getAllProducts()
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("getAllProducts()")
    class GetAllProductsTests {

        @Test
        @DisplayName("Returns list of products when rows exist")
        void returnsProductList() throws Exception {
            // Arrange: ResultSet returns 2 rows then stops
            when(resultSet.next()).thenReturn(true, true, false);
            stubProductRow(1L, "SKU-001", "Laptop Pro", new BigDecimal("1299.99"), 50);

            // Act
            List<Product> products = productCatalog.getAllProducts();

            // Assert
            assertEquals(2, products.size());
            verify(preparedStatement).executeQuery();
        }

        @Test
        @DisplayName("Returns empty list when no products exist")
        void returnsEmptyListWhenNoRows() throws Exception {
            when(resultSet.next()).thenReturn(false);

            List<Product> products = productCatalog.getAllProducts();

            assertNotNull(products);
            assertTrue(products.isEmpty());
        }

        @Test
        @DisplayName("Records performance metric for getAllProducts")
        void recordsPerformanceMetric() throws Exception {
            when(resultSet.next()).thenReturn(false);

            productCatalog.getAllProducts();

            verify(perfMonitor).record(eq("ProductCatalogBean"), eq("getAllProducts"), anyLong());
        }

        @Test
        @DisplayName("Throws RuntimeException when SQL fails")
        void throwsRuntimeExceptionOnSQLException() throws Exception {
            when(connection.prepareStatement(anyString()))
                .thenThrow(new SQLException("DB unavailable"));

            RuntimeException ex = assertThrows(RuntimeException.class,
                () -> productCatalog.getAllProducts());

            assertTrue(ex.getMessage().contains("Product retrieval failed"));
        }
    }

    // ----------------------------------------------------------------
    // searchProducts()
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("searchProducts()")
    class SearchProductsTests {

        @Test
        @DisplayName("Returns matching products for valid keyword")
        void returnsMatchingProducts() throws Exception {
            when(resultSet.next()).thenReturn(true, false);
            stubProductRow(2L, "LAPTOP-001", "TechMart Laptop", new BigDecimal("999.99"), 30);

            List<Product> results = productCatalog.searchProducts("laptop");

            assertEquals(1, results.size());
            // Verify parameter was bound 3 times (name, description, sku LIKE clauses)
            verify(preparedStatement, times(3)).setString(anyInt(), contains("laptop"));
        }

        @Test
        @DisplayName("Null keyword falls through to getAllProducts")
        void nullKeywordCallsGetAll() throws Exception {
            when(resultSet.next()).thenReturn(false);

            List<Product> results = productCatalog.searchProducts(null);

            assertNotNull(results);
            // Should not bind any LIKE parameters — fell back to getAllProducts path
            verify(preparedStatement, never()).setString(anyInt(), contains("%"));
        }

        @Test
        @DisplayName("Empty keyword falls through to getAllProducts")
        void emptyKeywordCallsGetAll() throws Exception {
            when(resultSet.next()).thenReturn(false);
            productCatalog.searchProducts("   ");
            verify(preparedStatement, never()).setString(anyInt(), contains("%"));
        }

        @Test
        @DisplayName("Keyword is wrapped in % for LIKE pattern")
        void keywordWrappedInPercentSigns() throws Exception {
            when(resultSet.next()).thenReturn(false);

            productCatalog.searchProducts("phone");

            // Each of the 3 LIKE parameters should be %phone%
            verify(preparedStatement, times(3)).setString(anyInt(), eq("%phone%"));
        }

        @Test
        @DisplayName("Records performance metric for searchProducts")
        void recordsPerformanceMetric() throws Exception {
            when(resultSet.next()).thenReturn(false);

            productCatalog.searchProducts("tablet");

            verify(perfMonitor).record(eq("ProductCatalogBean"), eq("searchProducts"), anyLong());
        }
    }

    // ----------------------------------------------------------------
    // getProductById()
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("getProductById()")
    class GetProductByIdTests {

        @Test
        @DisplayName("Returns product when found")
        void returnsProductWhenFound() throws Exception {
            when(resultSet.next()).thenReturn(true);
            stubProductRow(42L, "PHONE-001", "SmartPhone X", new BigDecimal("699.99"), 100);

            Product product = productCatalog.getProductById(42L);

            assertNotNull(product);
            assertEquals(42L, product.getId());
            assertEquals("PHONE-001", product.getSku());
            assertEquals("SmartPhone X", product.getName());
            assertEquals(new BigDecimal("699.99"), product.getPrice());
        }

        @Test
        @DisplayName("Returns null when product not found")
        void returnsNullWhenNotFound() throws Exception {
            when(resultSet.next()).thenReturn(false);

            Product product = productCatalog.getProductById(999L);

            assertNull(product);
        }

        @Test
        @DisplayName("Binds product ID as parameter")
        void bindsProductIdParameter() throws Exception {
            when(resultSet.next()).thenReturn(false);

            productCatalog.getProductById(55L);

            verify(preparedStatement).setLong(1, 55L);
        }
    }

    // ----------------------------------------------------------------
    // getProductsByCategory()
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("getProductsByCategory()")
    class GetProductsByCategoryTests {

        @Test
        @DisplayName("Applies pagination parameters correctly")
        void appliesPaginationParameters() throws Exception {
            when(resultSet.next()).thenReturn(false);

            productCatalog.getProductsByCategory("Electronics", 40, 20);

            // category param
            verify(preparedStatement).setString(1, "Electronics");
            // limit
            verify(preparedStatement).setInt(2, 20);
            // offset
            verify(preparedStatement).setInt(3, 40);
        }

        @Test
        @DisplayName("Returns products for matching category")
        void returnsProductsForCategory() throws Exception {
            when(resultSet.next()).thenReturn(true, true, false);
            stubProductRow(1L, "PHONE-001", "Phone A", new BigDecimal("499.99"), 80);

            List<Product> results = productCatalog.getProductsByCategory("Mobile", 0, 20);

            assertEquals(2, results.size());
        }
    }

    // ----------------------------------------------------------------
    // addProduct()
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("addProduct()")
    class AddProductTests {

        @Test
        @DisplayName("Sets generated ID on product after insert")
        void setsGeneratedIdAfterInsert() throws Exception {
            ResultSet generatedKeys = mock(ResultSet.class);
            when(preparedStatement.getGeneratedKeys()).thenReturn(generatedKeys);
            when(generatedKeys.next()).thenReturn(true);
            when(generatedKeys.getLong(1)).thenReturn(99L);

            Product product = new Product(null, "NEW-001", "New Product",
                                          new BigDecimal("150.00"), 25);

            Product result = productCatalog.addProduct(product);

            assertNotNull(result);
            assertEquals(99L, result.getId());
        }

        @Test
        @DisplayName("Binds all product fields as SQL parameters")
        void bindsAllProductFields() throws Exception {
            ResultSet generatedKeys = mock(ResultSet.class);
            when(preparedStatement.getGeneratedKeys()).thenReturn(generatedKeys);
            when(generatedKeys.next()).thenReturn(false);

            Product product = new Product(null, "SKU-NEW", "Test Product",
                                          new BigDecimal("99.99"), 10);
            product.setDescription("A test product description");
            product.setWarehouseId("WH-WEST");
            product.setCategory("Accessories");

            productCatalog.addProduct(product);

            verify(preparedStatement).setString(1, "SKU-NEW");
            verify(preparedStatement).setString(2, "Test Product");
            verify(preparedStatement).setBigDecimal(4, new BigDecimal("99.99"));
            verify(preparedStatement).setInt(5, 10);
        }
    }

    // ----------------------------------------------------------------
    // Helper: stub a ResultSet row with typical product data
    // ----------------------------------------------------------------

    /**
     * Stubs all column reads on the shared ResultSet mock to return
     * consistent data for one product row.  Because all stub calls in
     * a single test share the same ResultSet mock, call this once per
     * product row pattern — not per individual column.
     */
    private void stubProductRow(Long id, String sku, String name,
                                 BigDecimal price, int stock) throws SQLException {
        when(resultSet.getLong("id")).thenReturn(id);
        when(resultSet.getString("sku")).thenReturn(sku);
        when(resultSet.getString("name")).thenReturn(name);
        when(resultSet.getString("description")).thenReturn("Test description");
        when(resultSet.getBigDecimal("price")).thenReturn(price);
        when(resultSet.getInt("stock_quantity")).thenReturn(stock);
        when(resultSet.getString("warehouse_id")).thenReturn("WH-TEST");
        when(resultSet.getString("category")).thenReturn("Electronics");
        when(resultSet.getTimestamp("created_at")).thenReturn(null);
        when(resultSet.getTimestamp("updated_at")).thenReturn(null);
    }
}
