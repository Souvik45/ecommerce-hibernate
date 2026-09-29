# E-Commerce Hibernate ORM

A Maven + Hibernate 6 (JPA annotations) application managing an e-commerce domain:
**Category, Product, Users, Orders, OrderDetails**. Uses an in-memory **H2** database by default and can be switched to **MySQL** with a config change.

## Entity relationships

```
Category 1 ────< Product >──── * OrderDetails * ────> 1 Orders * ────> 1 Users
```

| Entity | Key fields | Relationships |
|---|---|---|
| Category | id, name (unique, not null), description | One-to-Many → Product (cascade ALL, orphanRemoval) |
| Product | id, name, price (decimal), stockQuantity, deleted | Many-to-One → Category (lazy) |
| Users | id, username (unique), password (hashed), email (unique), role (ADMIN/CUSTOMER) | One-to-Many → Orders |
| Orders | id, orderDate, totalAmount | Many-to-One → Users, One-to-Many → OrderDetails (cascade ALL) |
| OrderDetails | id, quantity, unitPrice | Many-to-One → Orders, Many-to-One → Product |

All to-one associations are `LAZY`; to-many associations are lazy by default. Where data is needed together (order + user + products), a `JOIN FETCH` named query loads it in a single SQL statement.

## Prerequisites
- JDK 17+
- Maven 3.8+
- MySQL 8 running locally

## Run
mvn clean test          # runs the CRUD tests (WARNING: clears tables in the configured database)
mvn compile exec:java   # runs the demo in App.java
```bash
mvn clean test          # runs the CRUD test suite (H2, no setup needed)
mvn compile exec:java   # runs the demo in App.java
```

## Switch to MySQL
## Configure MySQL
1. Start MySQL.
2. In src/main/resources/hibernate.cfg.xml, set your MySQL username and password.
   The `ecommerce` database is created automatically.
   schema.sql is the equivalent manual DDL.
3. Run `mvn compile exec:java` or run App.java from your IDE.

## Project layout

```
src/main/java/com/example/ecommerce/
  App.java                     demo runner + query helpers (named, criteria, pagination, placeOrder)
  entity/                      Category, Product, Users, Orders, OrderDetails, Role
  util/                        HibernateUtil (SessionFactory), PasswordUtil (PBKDF2 hashing)
src/main/resources/            hibernate.cfg.xml, schema.sql
src/test/java/.../ShopCrudTest.java
```

## Assignment checklist

- **Hibernate setup**: `pom.xml`, `hibernate.cfg.xml`, `HibernateUtil` (single `SessionFactory`, transaction helpers).
- **Entities and mappings**: JPA annotations, unique/not-null constraints, cascade and lazy fetching.
- **CRUD**: insert categories, products and users; create orders with several details (`App.placeOrder`); fetch an order with its user and products (`App.findOrderWithDetails`); update and delete via Hibernate `Session` (`persist`, `find`, `remove`).
- **Bonus**
  - Named queries: `Product.findByCategory`, `Orders.findWithDetails`
  - Criteria API: `App.search(session, name, minPrice, maxPrice)`
  - Soft delete: `@SQLDelete` + `@SQLRestriction("deleted = false")` on `Product`
  - Pagination: `App.findPage(session, page, size)` and `App.countProducts`

## Design notes
- Passwords are stored as salted PBKDF2-HMAC-SHA256 hashes (`PasswordUtil`), never in plain text.
- `unitPrice` is copied onto `OrderDetails` at purchase time, so later price changes don't rewrite order history.
- `placeOrder` locks product rows (`PESSIMISTIC_WRITE`), checks stock, decrements it and saves the order in one transaction. Any failure rolls the whole thing back.
- Soft-deleting a product keeps historical order rows intact.
