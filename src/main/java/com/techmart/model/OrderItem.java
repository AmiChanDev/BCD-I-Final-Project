package com.techmart.model;

import java.io.Serializable;
import java.math.BigDecimal;

public class OrderItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long productId;
    private String productSku;
    private String productName;
    private int quantity;
    private BigDecimal unitPrice;

    public OrderItem() {}

    public OrderItem(Long productId, String sku, String name, int qty, BigDecimal unitPrice) {
        this.productId   = productId;
        this.productSku  = sku;
        this.productName = name;
        this.quantity    = qty;
        this.unitPrice   = unitPrice;
    }

    public BigDecimal getSubtotal() {
        if (unitPrice == null) return BigDecimal.ZERO;
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    // ---- Getters & Setters ----

    public Long getId()                          { return id; }
    public void setId(Long id)                   { this.id = id; }

    public Long getProductId()                   { return productId; }
    public void setProductId(Long productId)     { this.productId = productId; }

    public String getProductSku()                { return productSku; }
    public void setProductSku(String sku)        { this.productSku = sku; }

    public String getProductName()               { return productName; }
    public void setProductName(String name)      { this.productName = name; }

    public int getQuantity()                     { return quantity; }
    public void setQuantity(int quantity)        { this.quantity = quantity; }

    public BigDecimal getUnitPrice()             { return unitPrice; }
    public void setUnitPrice(BigDecimal price)   { this.unitPrice = price; }

    @Override
    public String toString() {
        return "OrderItem{product='" + productSku + "', qty=" + quantity +
               ", unitPrice=" + unitPrice + ", subtotal=" + getSubtotal() + "}";
    }
}
