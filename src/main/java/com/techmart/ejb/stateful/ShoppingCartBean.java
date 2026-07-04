package com.techmart.ejb.stateful;

import com.techmart.model.CartItem;
import com.techmart.model.Order;
import com.techmart.model.OrderItem;
import com.techmart.util.PerformanceMonitor;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Logger;

@Stateful
@StatefulTimeout(value = 30, unit = java.util.concurrent.TimeUnit.MINUTES)
public class ShoppingCartBean {

    private static final Logger LOG = Logger.getLogger(ShoppingCartBean.class.getName());
    private static final String COMPONENT = "ShoppingCartBean";

    @EJB
    private PerformanceMonitor perfMonitor;

    private Map<Long, CartItem> cartItems;

    private String customerId;

    private long lastModified;

    @PostConstruct
    public void init() {
        cartItems = new LinkedHashMap<>();   // LinkedHashMap preserves insertion order
        lastModified = System.currentTimeMillis();
        LOG.info("Shopping cart initialised for new session");
    }

    @PreDestroy
    public void destroy() {
        LOG.info("Shopping cart destroyed for customer: " + customerId);
        cartItems.clear();
    }

    @PrePassivate
    public void onPassivate() {
        // CartItem is Serializable — nothing to release
        LOG.fine("Cart passivated for customer: " + customerId);
    }

    @PostActivate
    public void onActivate() {
        LOG.fine("Cart activated for customer: " + customerId);
    }

    // ------------------------------------------------------------------
    // Business Methods
    // ------------------------------------------------------------------

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void addItem(Long productId, String sku, String name, int quantity, BigDecimal unitPrice) {
        long start = System.currentTimeMillis();
        try {
            if (cartItems.containsKey(productId)) {
                cartItems.get(productId).incrementQuantity(quantity);
                LOG.fine("Incremented qty for product " + sku);
            } else {
                cartItems.put(productId, new CartItem(productId, sku, name, quantity, unitPrice));
                LOG.fine("Added new item to cart: " + sku);
            }
            lastModified = System.currentTimeMillis();
        } finally {
            perfMonitor.record(COMPONENT, "addItem", System.currentTimeMillis() - start);
        }
    }

    public void removeItem(Long productId) {
        long start = System.currentTimeMillis();
        try {
            cartItems.remove(productId);
            lastModified = System.currentTimeMillis();
        } finally {
            perfMonitor.record(COMPONENT, "removeItem", System.currentTimeMillis() - start);
        }
    }

    public void updateQuantity(Long productId, int newQuantity) {
        if (newQuantity <= 0) {
            removeItem(productId);
            return;
        }
        CartItem item = cartItems.get(productId);
        if (item != null) {
            item.setQuantity(newQuantity);
            lastModified = System.currentTimeMillis();
        }
    }
    public List<CartItem> getItems() {
        return Collections.unmodifiableList(new ArrayList<>(cartItems.values()));
    }

    public BigDecimal getTotal() {
        return cartItems.values().stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isEmpty() {
        return cartItems.isEmpty();
    }

    public int getItemCount() {
        return cartItems.values().stream().mapToInt(CartItem::getQuantity).sum();
    }

    public Order buildOrder(String customerEmail, String shippingAddress) {
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Cannot build an order from an empty cart");
        }

        Order order = new Order(customerId, customerEmail);
        order.setShippingAddress(shippingAddress);

        for (CartItem cartItem : cartItems.values()) {
            OrderItem orderItem = new OrderItem(
                cartItem.getProductId(),
                cartItem.getSku(),
                cartItem.getName(),
                cartItem.getQuantity(),
                cartItem.getUnitPrice()
            );
            order.addItem(orderItem);
        }

        return order;
    }

    @Remove
    public void checkout() {
        LOG.info("Checkout complete — cart cleared and bean removed for customer: " + customerId);
        cartItems.clear();
    }

    @Remove
    public void clearCart() {
        cartItems.clear();
        lastModified = System.currentTimeMillis();
        LOG.info("Cart cleared (session preserved) for customer: " + customerId);
    }

    public long getLastModified() {
        return lastModified;
    }
}
