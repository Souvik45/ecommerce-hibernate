package com.example.ecommerce;

import com.example.ecommerce.entity.*;
import com.example.ecommerce.util.HibernateUtil;
import com.example.ecommerce.util.PasswordUtil;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.Session;

/**
 * Demo runner (mvn compile exec:java). All database work uses Hibernate Sessions directly;
 * the static helpers below take a Session so the caller controls the transaction.
 */
public class App {

    public static void main(String[] args) {
        try {
            // 1. Insert categories + products (products cascade from category)
            Category electronics = new Category("Electronics", "Gadgets and devices");
            electronics.addProduct(new Product("Laptop", new BigDecimal("55000.00"), 10));
            electronics.addProduct(new Product("Headphones", new BigDecimal("1999.50"), 50));
            Category books = new Category("Books", "Printed and digital books");
            books.addProduct(new Product("Clean Code", new BigDecimal("650.00"), 30));

            // 2. Insert users (passwords are hashed)
            Users alice = new Users("alice", PasswordUtil.hash("secret"), "alice@shop.com", Role.CUSTOMER);
            Users admin = new Users("admin", PasswordUtil.hash("admin123"), "admin@shop.com", Role.ADMIN);

            HibernateUtil.runInTransaction(s -> {
                s.persist(electronics);
                s.persist(books);
                s.persist(alice);
                s.persist(admin);
            });

            // 3. Create an order with multiple order details
            Orders order = HibernateUtil.inTransaction(s -> {
                Map<Long, Integer> cart = new LinkedHashMap<>();
                for (Product p : findByCategory(s, electronics.getId())) cart.put(p.getId(), 2);
                return placeOrder(s, alice.getId(), cart);
            });

            // 4. Fetch order with user and products
            HibernateUtil.runInTransaction(s -> {
                Orders loaded = findOrderWithDetails(s, order.getId());
                System.out.println("\n=== Order #" + loaded.getId() + " by " + loaded.getUser().getUsername()
                        + " | total = " + loaded.getTotalAmount());
                for (OrderDetails d : loaded.getOrderDetails()) {
                    System.out.println(" - " + d.getProduct().getName() + " x" + d.getQuantity() + " @ " + d.getUnitPrice());
                }
            });

            // Bonus features
            HibernateUtil.runInTransaction(s -> {
                System.out.println("\nCriteria search 'phone' <= 5000: " + search(s, "phone", null, new BigDecimal("5000")));
                System.out.println("Page 1 (size 2): " + findPage(s, 0, 2) + " of " + countProducts(s) + " total");

                Product clean = findByCategory(s, books.getId()).get(0);
                s.remove(clean); // soft delete (see @SQLDelete on Product)
            });
            HibernateUtil.runInTransaction(s ->
                    System.out.println("After soft delete, Books has " + findByCategory(s, books.getId()).size() + " products"));
        } finally {
            HibernateUtil.shutdown();
        }
    }

    /** Named query: products of a category. */
    public static List<Product> findByCategory(Session s, Long categoryId) {
        return s.createNamedQuery("Product.findByCategory", Product.class)
                .setParameter("categoryId", categoryId)
                .getResultList();
    }

    /** CriteriaBuilder query with optional filters (any argument may be null). */
    public static List<Product> search(Session s, String nameContains, BigDecimal minPrice, BigDecimal maxPrice) {
        CriteriaBuilder cb = s.getCriteriaBuilder();
        CriteriaQuery<Product> cq = cb.createQuery(Product.class);
        Root<Product> root = cq.from(Product.class);

        List<Predicate> predicates = new ArrayList<>();
        if (nameContains != null && !nameContains.isBlank()) {
            predicates.add(cb.like(cb.lower(root.get("name")), "%" + nameContains.toLowerCase() + "%"));
        }
        if (minPrice != null) predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
        if (maxPrice != null) predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));

        cq.select(root).where(predicates.toArray(new Predicate[0])).orderBy(cb.asc(root.get("price")));
        return s.createQuery(cq).getResultList();
    }

    /** Pagination (zero-based page index). */
    public static List<Product> findPage(Session s, int page, int size) {
        return s.createQuery("FROM Product p ORDER BY p.id", Product.class)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    public static long countProducts(Session s) {
        return s.createQuery("SELECT COUNT(p) FROM Product p", Long.class).getSingleResult();
    }

    /**
     * Creates an order with one OrderDetails per product. Unit prices are copied from the product,
     * stock is decremented and the total is computed. Call inside a transaction.
     *
     * @param items productId -> quantity
     */
    public static Orders placeOrder(Session s, Long userId, Map<Long, Integer> items) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Order has no items");

        Users user = s.find(Users.class, userId);
        if (user == null) throw new IllegalArgumentException("No such user: " + userId);

        Orders order = new Orders();
        order.setUser(user);
        order.setOrderDate(LocalDateTime.now());

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> item : items.entrySet()) {
            int qty = item.getValue();
            if (qty < 1) throw new IllegalArgumentException("Quantity must be >= 1");

            Product p = s.find(Product.class, item.getKey(), LockModeType.PESSIMISTIC_WRITE);
            if (p == null) throw new IllegalArgumentException("No such product: " + item.getKey());

            int stock = p.getStockQuantity() == null ? 0 : p.getStockQuantity();
            if (stock < qty) {
                throw new IllegalStateException(
                        "Insufficient stock for '" + p.getName() + "': requested " + qty + ", available " + stock);
            }
            p.setStockQuantity(stock - qty);

            order.addDetail(new OrderDetails(p, qty, p.getPrice()));
            total = total.add(p.getPrice().multiply(BigDecimal.valueOf(qty)));
        }
        order.setTotalAmount(total);
        s.persist(order); // cascades to OrderDetails
        return order;
    }

    /** Fetches an order with its user, details and products in one query. */
    public static Orders findOrderWithDetails(Session s, Long orderId) {
        return s.createNamedQuery("Orders.findWithDetails", Orders.class)
                .setParameter("id", orderId)
                .uniqueResult();
    }
}
