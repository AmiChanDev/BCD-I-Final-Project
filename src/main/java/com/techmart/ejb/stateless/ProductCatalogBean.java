package com.techmart.ejb.stateless;

import com.techmart.model.Product;
import com.techmart.util.DataSourceProvider;
import com.techmart.util.PerformanceMonitor;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Stateless
public class ProductCatalogBean {

    private static final Logger LOG = Logger.getLogger(ProductCatalogBean.class.getName());
    private static final String COMPONENT = "ProductCatalogBean";

    @EJB
    private DataSourceProvider dsProvider;

    @EJB
    private PerformanceMonitor perfMonitor;

    @PostConstruct
    public void init() {
        // Called once after the container instantiates and injects this bean
        // instance. Good place for one-time setup that doesn't belong in the
        // constructor (injection is not complete until after the constructor).
        LOG.info("ProductCatalogBean instance created and ready for pool");
    }

    @PreDestroy
    public void cleanup() {
        // Called when the container removes this instance from the pool.
        // Release any non-container-managed resources here.
        LOG.info("ProductCatalogBean instance removed from pool");
    }

    // ------------------------------------------------------------------
    // Business Methods
    // ------------------------------------------------------------------

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public List<Product> getAllProducts() {
        long start = System.currentTimeMillis();
        List<Product> products = new ArrayList<>();

        String sql = "SELECT id, sku, name, description, price, stock_quantity, " +
                     "warehouse_id, category, created_at, updated_at " +
                     "FROM products ORDER BY name";

        try (Connection conn = dsProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                products.add(mapRow(rs));
            }

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to retrieve all products", e);
            throw new RuntimeException("Product retrieval failed", e);
        } finally {
            perfMonitor.record(COMPONENT, "getAllProducts", System.currentTimeMillis() - start);
        }

        return products;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public List<Product> searchProducts(String keyword) {
        long start = System.currentTimeMillis();
        List<Product> results = new ArrayList<>();

        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllProducts();
        }

        String pattern = "%" + keyword.trim() + "%";
        String sql = "SELECT id, sku, name, description, price, stock_quantity, " +
                     "warehouse_id, category, created_at, updated_at " +
                     "FROM products " +
                     "WHERE name LIKE ? OR description LIKE ? OR sku LIKE ? " +
                     "ORDER BY name";

        try (Connection conn = dsProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Product search failed for keyword: " + keyword, e);
            throw new RuntimeException("Product search failed", e);
        } finally {
            perfMonitor.record(COMPONENT, "searchProducts", System.currentTimeMillis() - start);
        }

        return results;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Product getProductById(Long productId) {
        long start = System.currentTimeMillis();

        String sql = "SELECT id, sku, name, description, price, stock_quantity, " +
                     "warehouse_id, category, created_at, updated_at " +
                     "FROM products WHERE id = ?";

        try (Connection conn = dsProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to find product id=" + productId, e);
            throw new RuntimeException("Product lookup failed", e);
        } finally {
            perfMonitor.record(COMPONENT, "getProductById", System.currentTimeMillis() - start);
        }

        return null;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public List<Product> getProductsByCategory(String category, int offset, int limit) {
        long start = System.currentTimeMillis();
        List<Product> results = new ArrayList<>();

        String sql = "SELECT id, sku, name, description, price, stock_quantity, " +
                     "warehouse_id, category, created_at, updated_at " +
                     "FROM products WHERE category = ? " +
                     "ORDER BY name LIMIT ? OFFSET ?";

        try (Connection conn = dsProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, category);
            ps.setInt(2, limit);
            ps.setInt(3, offset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Category search failed: " + category, e);
            throw new RuntimeException("Category search failed", e);
        } finally {
            perfMonitor.record(COMPONENT, "getProductsByCategory", System.currentTimeMillis() - start);
        }

        return results;
    }

    /**
     * Saves a new product to the catalogue.
     * Uses REQUIRED transaction so DB write is committed with the caller's tx.
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Product addProduct(Product product) {
        long start = System.currentTimeMillis();

        String sql = "INSERT INTO products (sku, name, description, price, stock_quantity, " +
                     "warehouse_id, category, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dsProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, product.getSku());
            ps.setString(2, product.getName());
            ps.setString(3, product.getDescription());
            ps.setBigDecimal(4, product.getPrice());
            ps.setInt(5, product.getStockQuantity());
            ps.setString(6, product.getWarehouseId());
            ps.setString(7, product.getCategory());
            ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            ps.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    product.setId(keys.getLong(1));
                }
            }

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to add product: " + product.getSku(), e);
            throw new RuntimeException("Product insert failed", e);
        } finally {
            perfMonitor.record(COMPONENT, "addProduct", System.currentTimeMillis() - start);
        }

        return product;
    }

    // ------------------------------------------------------------------
    // Private Helpers
    // ------------------------------------------------------------------

    private Product mapRow(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setId(rs.getLong("id"));
        p.setSku(rs.getString("sku"));
        p.setName(rs.getString("name"));
        p.setDescription(rs.getString("description"));
        p.setPrice(rs.getBigDecimal("price"));
        p.setStockQuantity(rs.getInt("stock_quantity"));
        p.setWarehouseId(rs.getString("warehouse_id"));
        p.setCategory(rs.getString("category"));

        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) p.setCreatedAt(created.toLocalDateTime());

        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) p.setUpdatedAt(updated.toLocalDateTime());

        return p;
    }
}
