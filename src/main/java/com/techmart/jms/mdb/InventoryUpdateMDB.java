package com.techmart.jms.mdb;

import com.techmart.ejb.singleton.InventoryTrackerBean;
import com.techmart.util.PerformanceMonitor;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.EJB;
import jakarta.ejb.MessageDriven;
import jakarta.jms.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@MessageDriven(
    activationConfig = {
        @ActivationConfigProperty(
            propertyName  = "destinationType",
            propertyValue = "jakarta.jms.Topic"
        ),
        @ActivationConfigProperty(
            // GlassFish uses destinationLookup for JNDI-registered destinations
            propertyName  = "destinationLookup",
            propertyValue = "jms/InventoryTopic"
        ),
        @ActivationConfigProperty(
            propertyName  = "acknowledgeMode",
            propertyValue = "Auto-acknowledge"
        ),
        @ActivationConfigProperty(
            propertyName  = "subscriptionDurability",
            propertyValue = "NonDurable"
        )
    }
)
public class InventoryUpdateMDB implements MessageListener {

    private static final Logger LOG = Logger.getLogger(InventoryUpdateMDB.class.getName());
    private static final String COMPONENT = "InventoryUpdateMDB";

    @EJB
    private InventoryTrackerBean inventoryTracker;

    @EJB
    private PerformanceMonitor perfMonitor;

    @PostConstruct
    public void init() {
        LOG.info("InventoryUpdateMDB subscribed to InventoryTopic");
    }

    @PreDestroy
    public void destroy() {
        LOG.info("InventoryUpdateMDB unsubscribed from InventoryTopic");
    }

    @Override
    public void onMessage(Message message) {
        long start = System.currentTimeMillis();

        try {
            if (!(message instanceof MapMessage)) {
                LOG.warning("Unexpected message type on InventoryTopic: "
                            + message.getClass().getName());
                return;
            }

            MapMessage mapMsg = (MapMessage) message;

            long productId  = mapMsg.getLong("productId");
            int newLevel    = mapMsg.getInt("newLevel");
            String type     = mapMsg.getString("eventType");
            long timestamp  = mapMsg.getLong("timestamp");

            LOG.fine("Inventory event received: product=" + productId +
                     ", level=" + newLevel + ", type=" + type);

            // Apply to local singleton cache (skips DB write — originator already wrote it)
            inventoryTracker.applyRemoteInventoryUpdate(productId, newLevel);

            LOG.info("Inventory cache updated: product=" + productId +
                     " → " + newLevel + " units [" + type + "]");

        } catch (JMSException e) {
            LOG.log(Level.SEVERE, "Failed to parse inventory update message", e);
            throw new RuntimeException("Inventory message parse error", e);

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unexpected error applying inventory update", e);
            throw new RuntimeException("Inventory update failed", e);

        } finally {
            perfMonitor.record(COMPONENT, "onMessage", System.currentTimeMillis() - start);
        }
    }
}
