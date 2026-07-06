package com.techmart.jms.producer;

import com.techmart.model.Order;
import com.techmart.util.PerformanceMonitor;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.jms.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Stateless
public class OrderMessageProducer {

    private static final Logger LOG = Logger.getLogger(OrderMessageProducer.class.getName());
    private static final String COMPONENT = "OrderMessageProducer";

    @Resource(lookup = "java:comp/DefaultJMSConnectionFactory")
    private ConnectionFactory connectionFactory;

    @Resource(lookup = "jms/OrderQueue")
    private Queue orderQueue;

    @EJB
    private PerformanceMonitor perfMonitor;

    @PostConstruct
    public void init() {
        LOG.info("OrderMessageProducer ready — targeting queue: jms/OrderQueue");
    }

    public void sendOrderForProcessing(Order order) {
        long start = System.currentTimeMillis();

        try (JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {

            JMSProducer producer = context.createProducer()
                    .setDeliveryMode(DeliveryMode.PERSISTENT)    // survives broker restart
                    .setTimeToLive(24 * 60 * 60 * 1000L)        // expire after 24 hours
                    .setPriority(Message.DEFAULT_PRIORITY);

            ObjectMessage message = context.createObjectMessage(order);

            // Custom properties allow MDB message selectors to filter
            message.setLongProperty("orderId", order.getId() != null ? order.getId() : -1L);
            message.setStringProperty("customerId", order.getCustomerId());
            message.setStringProperty("status", Order.Status.PENDING.name());

            producer.send(orderQueue, message);

            LOG.info("Order enqueued: orderId=" + order.getId() +
                     ", customer=" + order.getCustomerId());

        } catch (JMSException e) {
            LOG.log(Level.SEVERE, "Failed to enqueue order " + order.getId(), e);
            throw new RuntimeException("JMS send failed for order " + order.getId(), e);
        } finally {
            perfMonitor.record(COMPONENT, "sendOrderForProcessing",
                               System.currentTimeMillis() - start);
        }
    }
}
