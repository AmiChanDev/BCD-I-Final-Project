package com.techmart.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Status {
        PENDING, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED
    }

    private Long id;
    private String customerId;
    private String customerEmail;
    private List<OrderItem> items = new ArrayList<>();
    private BigDecimal totalAmount;
    private Status status;
    private String shippingAddress;
    private LocalDateTime placedAt;
    private LocalDateTime updatedAt;
    private String processingNotes;

    public Order() {
        this.status = Status.PENDING;
        this.placedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Order(String customerId, String customerEmail) {
        this();
        this.customerId = customerId;
        this.customerEmail = customerEmail;
    }

    public void recalculateTotal() {
        this.totalAmount = items.stream()
            .map(OrderItem::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.updatedAt = LocalDateTime.now();
    }

    public void addItem(OrderItem item) {
        this.items.add(item);
        recalculateTotal();
    }

    // ---- Getters & Setters ----

    public Long getId()                           { return id; }
    public void setId(Long id)                    { this.id = id; }

    public String getCustomerId()                 { return customerId; }
    public void setCustomerId(String id)          { this.customerId = id; }

    public String getCustomerEmail()              { return customerEmail; }
    public void setCustomerEmail(String email)    { this.customerEmail = email; }

    public List<OrderItem> getItems()             { return items; }
    public void setItems(List<OrderItem> items)   { this.items = items; }

    public BigDecimal getTotalAmount()            { return totalAmount; }
    public void setTotalAmount(BigDecimal amt)    { this.totalAmount = amt; }

    public Status getStatus()                     { return status; }
    public void setStatus(Status status)          { this.status = status; updatedAt = LocalDateTime.now(); }

    public String getShippingAddress()            { return shippingAddress; }
    public void setShippingAddress(String addr)   { this.shippingAddress = addr; }

    public LocalDateTime getPlacedAt()            { return placedAt; }
    public void setPlacedAt(LocalDateTime dt)     { this.placedAt = dt; }

    public LocalDateTime getUpdatedAt()           { return updatedAt; }
    public void setUpdatedAt(LocalDateTime dt)    { this.updatedAt = dt; }

    public String getProcessingNotes()            { return processingNotes; }
    public void setProcessingNotes(String notes)  { this.processingNotes = notes; }

    @Override
    public String toString() {
        return "Order{id=" + id + ", customer='" + customerId +
               "', items=" + items.size() + ", total=" + totalAmount +
               ", status=" + status + "}";
    }
}
