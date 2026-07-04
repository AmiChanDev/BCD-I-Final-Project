package com.techmart.model;

import java.io.Serializable;
import java.math.BigDecimal;

public class CartItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long productId;
    private String sku;
    private String name;
    private int quantity;
    private BigDecimal unitPrice;

    public CartItem() {}

    public CartItem(Long productId, String sku, String name, int quantity, BigDecimal unitPrice) {
        this.productId = productId;
        this.sku       = sku;
        this.name      = name;
        this.quantity  = quantity;
        this.unitPrice = unitPrice;
    }

    public BigDecimal getSubtotal() {
        if (unitPrice == null) return BigDecimal.ZERO;
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public void incrementQuantity(int delta) {
        this.quantity += delta;
    }

    // ---- Getters & Setters ----

    public Long getProductId()                 { return productId; }
    public void setProductId(Long id)          { this.productId = id; }

    public String getSku()                     { return sku; }
    public void setSku(String sku)             { this.sku = sku; }

    public String getName()                    { return name; }
    public void setName(String name)           { this.name = name; }

    public int getQuantity()                   { return quantity; }
    public void setQuantity(int quantity)      { this.quantity = quantity; }

    public BigDecimal getUnitPrice()           { return unitPrice; }
    public void setUnitPrice(BigDecimal price) { this.unitPrice = price; }

    @Override
    public String toString() {
        return "CartItem{sku='" + sku + "', qty=" + quantity + ", subtotal=" + getSubtotal() + "}";
    }
}
