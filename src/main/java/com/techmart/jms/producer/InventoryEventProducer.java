package com.techmart.jms.producer;

import com.techmart.util.PerformanceMonitor;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.jms.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Stateless
public class InventoryEventProducer {

    private static final Logger LOG = Logger.getLogger(InventoryEventProducer.class.getName());
    private static final String COMPONENT = "InventoryEventProducer";

    @Resource(lookup = "java:comp/DefaultJMSConnectionFactory")
    private ConnectionFactory connectionFactory;

    /** Inventory topic for pub/sub broadcast */
    @Resource(lookup = "jms/InventoryTopic")
    private Topic inventoryTopic;

    @EJB
    private PerformanceMonitor perfMonitor;

    @PostConstruct
    public void init() {
        LOG.info("InventoryEventProducer ready — targeting topic: jms/InventoryTopic");
    }

    public void publishInventoryUpdate(Long productId, int newLevel, String eventType) {
        long start = System.currentTimeMillis();

        try (JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {

            MapMessage message = context.createMapMessage();
            message.setLong("productId", productId);
            message.setInt("newLevel", newLevel);
            message.setString("eventType", eventType);
            message.setLong("timestamp", System.currentTimeMillis());

            // Message selector property — subscribers can filter by eventType
            message.setStringProperty("eventType", eventType);

            context.createProducer()
                   .setDeliveryMode(DeliveryMode.NON_PERSISTENT)
                   .setTimeToLive(60_000L)   // 1 minute TTL — stale inventory events are useless
                   .send(inventoryTopic, message);

            LOG.fine("Inventory event published: product=" + productId +
                     ", level=" + newLevel + ", type=" + eventType);

        } catch (JMSException e) {
            LOG.log(Level.WARNING,
                    "Failed to publish inventory event for product " + productId, e);
            // Non-fatal — local cache is already updated; log for monitoring
            throw new RuntimeException("Inventory publish failed", e);
        } finally {
            perfMonitor.record(COMPONENT, "publishInventoryUpdate",
                               System.currentTimeMillis() - start);
        }
    }
}
