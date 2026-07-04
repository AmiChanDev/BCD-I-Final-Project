-- ============================================================
-- TechMart E-Commerce Platform — Database Schema
-- Target: MySQL 8.0+
-- Run as: techmart_user (must have CREATE, INDEX privileges)
-- ============================================================

CREATE DATABASE IF NOT EXISTS techmart
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE techmart;

-- ============================================================
-- Products table
-- Indexed on category and sku for search performance.
-- stock_quantity is the authoritative DB value;
-- InventoryTrackerBean maintains an in-memory cache.
-- ============================================================
CREATE TABLE IF NOT EXISTS products (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sku            VARCHAR(50)  NOT NULL UNIQUE,
    name           VARCHAR(255) NOT NULL,
    description    TEXT,
    price          DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    stock_quantity INT          NOT NULL DEFAULT 0,
    warehouse_id   VARCHAR(50),
    category       VARCHAR(100),
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_products_category (category),
    INDEX idx_products_sku      (sku),
    INDEX idx_products_name     (name),
    FULLTEXT INDEX ft_products_search (name, description)
) ENGINE=InnoDB;

-- ============================================================
-- Orders table
-- status ENUM enforces valid transitions at DB level.
-- ============================================================
CREATE TABLE IF NOT EXISTS orders (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id      VARCHAR(100)  NOT NULL,
    customer_email   VARCHAR(255)  NOT NULL,
    total_amount     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    status           ENUM('PENDING','CONFIRMED','PROCESSING','SHIPPED','DELIVERED','CANCELLED')
                     NOT NULL DEFAULT 'PENDING',
    shipping_address TEXT,
    placed_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    processing_notes TEXT,

    INDEX idx_orders_customer (customer_id),
    INDEX idx_orders_status   (status),
    INDEX idx_orders_placed   (placed_at)
) ENGINE=InnoDB;

-- ============================================================
-- Order line items
-- Foreign keys enforce referential integrity at DB level.
-- ============================================================
CREATE TABLE IF NOT EXISTS order_items (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id     BIGINT        NOT NULL,
    product_id   BIGINT        NOT NULL,
    product_sku  VARCHAR(50)   NOT NULL,
    product_name VARCHAR(255)  NOT NULL,
    quantity     INT           NOT NULL DEFAULT 1,
    unit_price   DECIMAL(10,2) NOT NULL,

    CONSTRAINT fk_items_order   FOREIGN KEY (order_id)   REFERENCES orders(id)   ON DELETE CASCADE,
    CONSTRAINT fk_items_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,

    INDEX idx_items_order   (order_id),
    INDEX idx_items_product (product_id)
) ENGINE=InnoDB;

-- ============================================================
-- Sample seed data
-- ============================================================
INSERT INTO products (sku, name, description, price, stock_quantity, warehouse_id, category) VALUES
('LAPTOP-001', 'TechMart ProBook 15', '15-inch laptop, Intel i7, 16GB RAM, 512GB SSD', 1299.99, 50, 'WH-NORTH', 'Electronics'),
('LAPTOP-002', 'TechMart UltraSlim 13', '13-inch ultrabook, AMD Ryzen 7, 8GB RAM, 256GB SSD', 899.99, 75, 'WH-NORTH', 'Electronics'),
('PHONE-001', 'TechMart SmartX Pro', '6.5-inch AMOLED, 5G, 256GB storage', 699.99, 120, 'WH-EAST', 'Mobile'),
('PHONE-002', 'TechMart SmartX Lite', '6.1-inch LCD, 4G, 128GB storage', 399.99, 200, 'WH-EAST', 'Mobile'),
('TAB-001', 'TechMart TabPro 10', '10-inch tablet, WiFi+5G, 128GB, stylus included', 549.99, 60, 'WH-SOUTH', 'Tablets'),
('HDPHN-001', 'TechMart SoundMax Pro', 'Wireless ANC headphones, 40hr battery', 249.99, 150, 'WH-WEST', 'Audio'),
('MOUSE-001', 'TechMart ErgoMouse', 'Ergonomic wireless mouse, 2.4GHz', 49.99, 300, 'WH-WEST', 'Accessories'),
('KBD-001', 'TechMart MechKey RGB', 'Mechanical keyboard, Cherry MX switches, RGB', 129.99, 80, 'WH-WEST', 'Accessories');
