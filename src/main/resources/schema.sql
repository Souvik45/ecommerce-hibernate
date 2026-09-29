-- MySQL-compatible schema equivalent to what Hibernate generates.
CREATE DATABASE IF NOT EXISTS ecommerce;
USE ecommerce;

CREATE TABLE category (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    CONSTRAINT uk_category_name UNIQUE (name)
);

CREATE TABLE product (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(150)   NOT NULL,
    price          DECIMAL(10, 2) NOT NULL,
    stock_quantity INT,
    deleted        BOOLEAN        NOT NULL DEFAULT FALSE,
    category_id    BIGINT,
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id)
);

CREATE TABLE users (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50)  NOT NULL,
    password VARCHAR(255) NOT NULL,
    email    VARCHAR(150) NOT NULL,
    role     VARCHAR(20)  NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'CUSTOMER'))
);

CREATE TABLE orders (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_date   DATETIME(6)    NOT NULL,
    total_amount DECIMAL(12, 2) NOT NULL,
    user_id      BIGINT         NOT NULL,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE order_details (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    quantity   INT            NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    order_id   BIGINT         NOT NULL,
    product_id BIGINT         NOT NULL,
    CONSTRAINT fk_od_order   FOREIGN KEY (order_id)   REFERENCES orders (id),
    CONSTRAINT fk_od_product FOREIGN KEY (product_id) REFERENCES product (id)
);

CREATE INDEX idx_product_category ON product (category_id);
CREATE INDEX idx_orders_user      ON orders (user_id);
CREATE INDEX idx_od_order         ON order_details (order_id);
