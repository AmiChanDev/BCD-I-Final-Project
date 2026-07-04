package com.techmart.jms.mdb;

import com.techmart.ejb.singleton.InventoryTrackerBean;
import com.techmart.ejb.stateless.OrderServiceBean;
import com.techmart.model.Order;
import com.techmart.model.OrderItem;
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
            propertyValue = "jakarta.jms.Queue"
        ),
        @ActivationConfigProperty(
            // GlassFish uses destinationLookup (JNDI) rather than
            // the physical destination name used by some other containers.
            propertyName  = "destinationLookup",
            propertyValue = "jms/OrderQueue"
        ),
        @ActivationConfigProperty(
            propertyName  = "acknowledgeMode",
            propertyValue = "Auto-acknowledge"
        ),
        @ActivationConfigProperty(
            // GlassFish Open MQ: max redelivery attempts before dead message queue
            propertyName  = "endpointExceptionRedeliveryAttempts",
            propertyValue = "3"
        ),
        @ActivationConfigProperty(
            propertyName  = "maxSession",
            propertyValue = "10"
        )
    }
)
public class OrderProcessorMDB implements MessageListener {

    private static final Logger LOG = Logger.getLogger(OrderProcessorMDB.class.getName());
    private static final String COMPONENT = "OrderProcessorMDB";

    @EJB
    private InventoryTrackerBean inventoryTracker;

    @EJB
    private OrderServiceBean orderService;

    @EJB
    private PerformanceMonitor perfMonitor;

    @PostConstruct
    public void init() {
        LOG.info("OrderProcessorMDB instance ready in pool");
    }

    @PreDestroy
    public void destroy() {
        LOG.info("OrderProcessorMDB instance removed from pool");
    }

    @Override
    public void onMessage(Message message) {
        long start = System.currentTimeMillis();
        Long orderId = null;

        try {
            if (!(message instanceof ObjectMessage)) {
                LOG.warning("Unexpected message type received: " + message.getClass().getName());
                return; // Acknowledge and discard — wrong message type
            }

            ObjectMessage objectMessage = (ObjectMessage) message;
            Order order = (Order) objectMessage.getObject();
            orderId = order.getId();

            LOG.info("Processing order: " + orderId + " for customer: " + order.getCustomerId());

            // Step 1 — Reserve inventory for every line item
            boolean allReserved = reserveInventoryForOrder(order);

            if (allReserved) {
                // Step 2a — All stock available: advance to PROCESSING
                orderService.updateOrderStatus(
                    orderId,
                    Order.Status.PROCESSING,
                    "Inventory reserved. Order dispatched to fulfilment."
                );
                LOG.info("Order " + orderId + " → PROCESSING");

            } else {
                // Step 2b — Insufficient stock: hold order for review
                orderService.updateOrderStatus(
                    orderId,
                    Order.Status.CANCELLED,
                    "Order cancelled: insufficient stock for one or more items."
                );
                LOG.warning("Order " + orderId + " cancelled — stock unavailable");
            }

        } catch (JMSException e) {
            LOG.log(Level.SEVERE, "JMS error processing order " + orderId, e);
            // Throw RuntimeException to trigger tx rollback and message redeliver
            throw new RuntimeException("JMS deserialization failed", e);

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unexpected error processing order " + orderId, e);
            throw new RuntimeException("Order processing failed", e);

        } finally {
            perfMonitor.record(COMPONENT, "onMessage", System.currentTimeMillis() - start);
        }
    }

    private boolean reserveInventoryForOrder(Order order) {
        java.util.List<OrderItem> reservedSoFar = new java.util.ArrayList<>();

        for (OrderItem item : order.getItems()) {
            boolean reserved = inventoryTracker.reserveStock(
                item.getProductId(), item.getQuantity()
            );

            if (!reserved) {
                // Compensating transaction — return what we already reserved
                LOG.warning("Stock unavailable for product " + item.getProductSku() +
                            " (qty=" + item.getQuantity() + "). Rolling back reserved items.");
                for (OrderItem alreadyReserved : reservedSoFar) {
                    inventoryTracker.restockProduct(
                        alreadyReserved.getProductId(), alreadyReserved.getQuantity()
                    );
                }
                return false;
            }

            reservedSoFar.add(item);
        }

        return true;
    }
}
