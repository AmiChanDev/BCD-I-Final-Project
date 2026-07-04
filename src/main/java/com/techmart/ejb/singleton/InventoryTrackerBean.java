package com.techmart.ejb.singleton;

import com.techmart.jms.producer.InventoryEventProducer;
import com.techmart.util.DataSourceProvider;
import com.techmart.util.PerformanceMonitor;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.*;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
@Singleton
@Startup
@ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
public class InventoryTrackerBean {

    private static final Logger LOG = Logger.getLogger(InventoryTrackerBean.class.getName());
    private static final String COMPONENT = "InventoryTrackerBean";

    @EJB
    private DataSourceProvider dsProvider;

    @EJB
    private PerformanceMonitor perfMonitor;

    @EJB
    private InventoryEventProducer inventoryProducer;

    private final Map<Long, Integer> inventoryCache = new HashMap<>();

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------
    @PostConstruct
    public void loadInventory() {
        long start = System.currentTimeMillis();
        LOG.info("InventoryTrackerBean: loading inventory from database...");

        String sql = "SELECT id, stock_quantity FROM products";
        try (Connection conn = dsProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            inventoryCache.clear();
            while (rs.next()) {
                inventoryCache.put(rs.getLong("id"), rs.getInt("stock_quantity"));
            }

            LOG.info("Inventory cache loaded: " + inventoryCache.size() + " products");

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to load inventory from DB", e);
            // Non-fatal at startup — DB might not be seeded yet
        } finally {
            perfMonitor.record(COMPONENT, "loadInventory", System.currentTimeMillis() - start);
        }
    }

    @PreDestroy
    public void flushInventory() {
        LOG.info("InventoryTrackerBean: flushing cache to DB on shutdown...");
        // In production: write all dirty entries back to the DB
        inventoryCache.clear();
    }

    // ------------------------------------------------------------------
    // Business Methods
    // ------------------------------------------------------------------

    @Lock(LockType.READ)
    public int getStockLevel(Long productId) {
        return inventoryCache.getOrDefault(productId, 0);
    }

    @Lock(LockType.READ)
    public boolean isAvailable(Long productId, int requestedQty) {
        return getStockLevel(productId) >= requestedQty;
    }

    @Lock(LockType.READ)
    public Map<Long, Integer> getFullInventory() {
        return new HashMap<>(inventoryCache);  // defensive copy
    }

    @Lock(LockType.WRITE)
    public boolean reserveStock(Long productId, int quantity) {
        long start = System.currentTimeMillis();
        try {
            int current = inventoryCache.getOrDefault(productId, 0);

            if (current < quantity) {
                LOG.warning("Insufficient stock for product " + productId +
                            ": requested=" + quantity + ", available=" + current);
                return false;
            }

            int newLevel = current - quantity;
            inventoryCache.put(productId, newLevel);

            // Persist immediately to DB
            updateStockInDb(productId, newLevel);

            // Publish change to other nodes via JMS topic
            publishInventoryChange(productId, newLevel, "RESERVE");

            LOG.info("Stock reserved: product=" + productId + ", qty=" + quantity +
                     ", remaining=" + newLevel);
            return true;

        } finally {
            perfMonitor.record(COMPONENT, "reserveStock", System.currentTimeMillis() - start);
        }
    }

    @Lock(LockType.WRITE)
    public void restockProduct(Long productId, int quantity) {
        long start = System.currentTimeMillis();
        try {
            int current = inventoryCache.getOrDefault(productId, 0);
            int newLevel = current + quantity;
            inventoryCache.put(productId, newLevel);

            updateStockInDb(productId, newLevel);
            publishInventoryChange(productId, newLevel, "RESTOCK");

            LOG.info("Product restocked: product=" + productId +
                     ", added=" + quantity + ", new level=" + newLevel);
        } finally {
            perfMonitor.record(COMPONENT, "restockProduct", System.currentTimeMillis() - start);
        }
    }

    @Lock(LockType.WRITE)
    public void applyRemoteInventoryUpdate(Long productId, int newLevel) {
        inventoryCache.put(productId, newLevel);
        LOG.fine("Applied remote inventory update: product=" + productId + ", level=" + newLevel);
    }

    // ------------------------------------------------------------------
    // Private Helpers
    // ------------------------------------------------------------------

    private void updateStockInDb(Long productId, int newLevel) {
        String sql = "UPDATE products SET stock_quantity = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = dsProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, newLevel);
            ps.setTimestamp(2, Timestamp.valueOf(java.time.LocalDateTime.now()));
            ps.setLong(3, productId);
            ps.executeUpdate();

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to persist stock update for product " + productId, e);
        }
    }

    private void publishInventoryChange(Long productId, int newLevel, String eventType) {
        try {
            inventoryProducer.publishInventoryUpdate(productId, newLevel, eventType);
        } catch (Exception e) {
            // Non-fatal — log and continue; cache is still consistent locally
            LOG.log(Level.WARNING,
                    "Failed to publish inventory event for product " + productId, e);
        }
    }
}
