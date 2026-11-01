-- =====================================================================
-- RIMS database schema (MySQL 8). Safe to run repeatedly on an EMPTY or already-migrated database.
-- Also loads on H2 in MySQL mode (used by the "h2" profile and tests).
--
-- UPGRADING: this version added locations, stock_levels, products.barcode, products.image_url and
-- location columns on purchase_orders, sales and inventory_transactions. "CREATE TABLE IF NOT
-- EXISTS" does not alter tables created by an older version, so for a development database
-- created earlier, drop it (DROP DATABASE rims_db;) and restart to recreate it with sample data.
--
-- Design notes
--  * All foreign keys are RESTRICT (no cascading deletes), so purchase and
--    sales history can never be orphaned or silently removed.
--  * Products are soft-deleted (products.active) once they have history.
--  * products.quantity is the TOTAL across locations. stock_levels holds the per-location split.
--    Only InventoryService changes either, always together, so the two never disagree.
--  * CHECK constraints are a last line of defence behind the service layer:
--    stock can never be stored as a negative number, in total or at any location.
--  * Money is DECIMAL(12,2)/(14,2); stock quantities are INT.
-- =====================================================================

CREATE TABLE IF NOT EXISTS users (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    username    VARCHAR(50)  NOT NULL,
    password    VARCHAR(100) NOT NULL,
    full_name   VARCHAR(100) NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'STAFF'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS categories (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT uk_categories_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS suppliers (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(150) NOT NULL,
    contact_number VARCHAR(30),
    email          VARCHAR(120),
    address        VARCHAR(255),
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_suppliers_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS locations (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    address    VARCHAR(255),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_locations_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS products (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    name          VARCHAR(150)   NOT NULL,
    sku           VARCHAR(50)    NOT NULL,
    barcode       VARCHAR(64)    NULL,
    image_url     VARCHAR(500)   NULL,
    category_id   BIGINT         NOT NULL,
    supplier_id   BIGINT         NULL,
    price         DECIMAL(12, 2) NOT NULL,
    quantity      INT            NOT NULL DEFAULT 0,
    reorder_level INT            NOT NULL DEFAULT 0,
    active        BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_products_sku UNIQUE (sku),
    CONSTRAINT uk_products_barcode UNIQUE (barcode),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_products_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT chk_products_price CHECK (price >= 0),
    CONSTRAINT chk_products_quantity CHECK (quantity >= 0),
    CONSTRAINT chk_products_reorder CHECK (reorder_level >= 0),
    INDEX idx_products_category (category_id),
    INDEX idx_products_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS stock_levels (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    product_id  BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    quantity    INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_stock_product_location UNIQUE (product_id, location_id),
    CONSTRAINT fk_stock_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_stock_location FOREIGN KEY (location_id) REFERENCES locations (id),
    CONSTRAINT chk_stock_quantity CHECK (quantity >= 0),
    INDEX idx_stock_location (location_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS purchase_orders (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    po_number     VARCHAR(40)    NOT NULL,
    supplier_id   BIGINT         NOT NULL,
    location_id   BIGINT         NOT NULL,
    status        VARCHAR(20)    NOT NULL,
    order_date    DATETIME       NOT NULL,
    received_date DATETIME       NULL,
    total_amount  DECIMAL(14, 2) NOT NULL,
    notes         VARCHAR(255),
    created_by    BIGINT         NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_po_number UNIQUE (po_number),
    CONSTRAINT fk_po_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_po_location FOREIGN KEY (location_id) REFERENCES locations (id),
    CONSTRAINT fk_po_user FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT chk_po_status CHECK (status IN ('PENDING', 'RECEIVED', 'CANCELLED')),
    INDEX idx_po_status (status),
    INDEX idx_po_order_date (order_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS purchase_order_items (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    purchase_order_id BIGINT         NOT NULL,
    product_id        BIGINT         NOT NULL,
    quantity          INT            NOT NULL,
    unit_cost         DECIMAL(12, 2) NOT NULL,
    line_total        DECIMAL(14, 2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_poi_order_product UNIQUE (purchase_order_id, product_id),
    CONSTRAINT fk_poi_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id),
    CONSTRAINT fk_poi_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_poi_quantity CHECK (quantity > 0),
    CONSTRAINT chk_poi_cost CHECK (unit_cost >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sales (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    sale_number   VARCHAR(40)    NOT NULL,
    sale_date     DATETIME       NOT NULL,
    location_id   BIGINT         NOT NULL,
    customer_name VARCHAR(100),
    total_amount  DECIMAL(14, 2) NOT NULL,
    created_by    BIGINT         NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sale_number UNIQUE (sale_number),
    CONSTRAINT fk_sale_location FOREIGN KEY (location_id) REFERENCES locations (id),
    CONSTRAINT fk_sale_user FOREIGN KEY (created_by) REFERENCES users (id),
    INDEX idx_sales_date (sale_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sale_items (
    id         BIGINT         NOT NULL AUTO_INCREMENT,
    sale_id    BIGINT         NOT NULL,
    product_id BIGINT         NOT NULL,
    quantity   INT            NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    line_total DECIMAL(14, 2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_si_sale_product UNIQUE (sale_id, product_id),
    CONSTRAINT fk_si_sale FOREIGN KEY (sale_id) REFERENCES sales (id),
    CONSTRAINT fk_si_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_si_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inventory_transactions (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    product_id      BIGINT       NOT NULL,
    location_id     BIGINT       NOT NULL,
    type            VARCHAR(30)  NOT NULL,
    quantity_change INT          NOT NULL,
    quantity_after  INT          NOT NULL,
    reference_type  VARCHAR(30),
    reference_id    BIGINT,
    note            VARCHAR(255),
    created_by      BIGINT       NULL,
    created_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_it_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_it_location FOREIGN KEY (location_id) REFERENCES locations (id),
    CONSTRAINT fk_it_user FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT chk_it_type CHECK (type IN ('INITIAL_STOCK', 'PURCHASE_RECEIVED', 'SALE', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'TRANSFER_IN', 'TRANSFER_OUT')),
    CONSTRAINT chk_it_after CHECK (quantity_after >= 0),
    INDEX idx_it_product (product_id),
    INDEX idx_it_location (location_id),
    INDEX idx_it_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
