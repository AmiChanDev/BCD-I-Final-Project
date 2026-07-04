package com.techmart.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String sku;
    private String name;
    private String description;
    private BigDecimal price;
    private int stockQuantity;
    private String warehouseId;
    private String category;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Product() {}

    public Product(Long id, String sku, String name, BigDecimal price, int stockQuantity) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // ---- Getters & Setters ----

    public Long getId()                        { return id; }
    public void setId(Long id)                 { this.id = id; }

    public String getSku()                     { return sku; }
    public void setSku(String sku)             { this.sku = sku; }

    public String getName()                    { return name; }
    public void setName(String name)           { this.name = name; }

    public String getDescription()             { return description; }
    public void setDescription(String desc)    { this.description = desc; }

    public BigDecimal getPrice()               { return price; }
    public void setPrice(BigDecimal price)     { this.price = price; }

    public int getStockQuantity()              { return stockQuantity; }
    public void setStockQuantity(int qty)      { this.stockQuantity = qty; }

    public String getWarehouseId()             { return warehouseId; }
    public void setWarehouseId(String wh)      { this.warehouseId = wh; }

    public String getCategory()                { return category; }
    public void setCategory(String category)   { this.category = category; }

    public LocalDateTime getCreatedAt()        { return createdAt; }
    public void setCreatedAt(LocalDateTime dt) { this.createdAt = dt; }

    public LocalDateTime getUpdatedAt()        { return updatedAt; }
    public void setUpdatedAt(LocalDateTime dt) { this.updatedAt = dt; }

    @Override
    public String toString() {
        return "Product{id=" + id + ", sku='" + sku + "', name='" + name +
               "', price=" + price + ", stock=" + stockQuantity + "}";
    }
}
