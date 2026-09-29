package com.example.ecommerce;

import static org.junit.jupiter.api.Assertions.*;

import com.example.ecommerce.entity.*;
import com.example.ecommerce.util.HibernateUtil;
import com.example.ecommerce.util.PasswordUtil;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.*;

class ShopCrudTest {

    @BeforeAll
    static void start() {
        HibernateUtil.getSessionFactory();
    }

    @AfterAll
    static void stop() {
        HibernateUtil.shutdown();
    }

    /** Native deletes so soft-deleted rows are cleaned too. */
    @BeforeEach
    void clean() {
        HibernateUtil.runInTransaction(s -> {
            for (String t : List.of("order_details", "orders", "product", "category", "users")) {
                s.createNativeMutationQuery("delete from " + t).executeUpdate();
            }
        });
    }

    private <T> T save(T entity) {
        return HibernateUtil.inTransaction(s -> { s.persist(entity); return entity; });
    }

    private Category newCategory(String name, int productCount) {
        Category c = new Category(name, name + " desc");
        for (int i = 1; i <= productCount; i++) {
            c.addProduct(new Product(name + " item " + i, new BigDecimal(i * 100 + ".00"), 10));
        }
        return save(c);
    }

    private Users newUser(String username) {
        return save(new Users(username, PasswordUtil.hash("pw"), username + "@shop.com", Role.CUSTOMER));
    }

    private List<Product> productsOf(Category c) {
        return HibernateUtil.query(s -> App.findByCategory(s, c.getId()));
    }

    private Product findProduct(Long id) {
        return HibernateUtil.query(s -> s.find(Product.class, id));
    }

    // ---------- CREATE / READ ----------

    @Test
    void insertCategoryWithProductsCascades() {
        Category c = newCategory("Electronics", 2);
        assertNotNull(c.getId());
        List<Product> products = productsOf(c); // named query
        assertEquals(2, products.size());
        products.forEach(p -> assertNotNull(p.getId()));
    }

    @Test
    void categoryNameMustBeUnique() {
        newCategory("Books", 0);
        assertThrows(RuntimeException.class, () -> save(new Category("Books", "duplicate")));
    }

    @Test
    void usernameAndEmailMustBeUnique() {
        newUser("bob");
        assertThrows(RuntimeException.class, () -> newUser("bob"));
    }

    @Test
    void passwordIsHashedAndVerifiable() {
        Users u = save(new Users("carol", PasswordUtil.hash("s3cret"), "carol@shop.com", Role.ADMIN));
        Users loaded = HibernateUtil.query(s -> s.find(Users.class, u.getId()));
        assertNotEquals("s3cret", loaded.getPassword());
        assertTrue(PasswordUtil.verify("s3cret", loaded.getPassword()));
        assertFalse(PasswordUtil.verify("wrong", loaded.getPassword()));
        assertEquals(Role.ADMIN, loaded.getRole());
    }

    // ---------- ORDERS ----------

    @Test
    void placeOrderWithMultipleDetailsAndFetchEverything() {
        Category c = newCategory("Gadgets", 2);
        Users u = newUser("alice");
        List<Product> products = productsOf(c);

        Map<Long, Integer> cart = new LinkedHashMap<>();
        cart.put(products.get(0).getId(), 2); // 100.00 x 2
        cart.put(products.get(1).getId(), 1); // 200.00 x 1
        Orders order = HibernateUtil.inTransaction(s -> App.placeOrder(s, u.getId(), cart));

        Orders loaded = HibernateUtil.query(s -> App.findOrderWithDetails(s, order.getId()));
        // usable after the session is closed => fetched eagerly by the join-fetch query
        assertEquals(0, new BigDecimal("400.00").compareTo(loaded.getTotalAmount()));
        assertTrue(Hibernate.isInitialized(loaded.getUser()));
        assertEquals("alice", loaded.getUser().getUsername());
        assertEquals(2, loaded.getOrderDetails().size());
        loaded.getOrderDetails().forEach(d -> assertNotNull(d.getProduct().getName()));

        assertEquals(8, findProduct(products.get(0).getId()).getStockQuantity());
        assertEquals(9, findProduct(products.get(1).getId()).getStockQuantity());
    }

    @Test
    void orderFailsAndRollsBackWhenStockIsInsufficient() {
        Category c = newCategory("Rare", 1);
        Users u = newUser("dave");
        Long pid = productsOf(c).get(0).getId();

        assertThrows(IllegalStateException.class,
                () -> HibernateUtil.inTransaction(s -> App.placeOrder(s, u.getId(), Map.of(pid, 999))));
        assertEquals(10, findProduct(pid).getStockQuantity());
        long orders = HibernateUtil.query(s -> s.createQuery("SELECT COUNT(o) FROM Orders o", Long.class).getSingleResult());
        assertEquals(0, orders);
    }

    // ---------- UPDATE / DELETE ----------

    @Test
    void updateProductPrice() {
        Category c = newCategory("Toys", 1);
        Long pid = productsOf(c).get(0).getId();
        HibernateUtil.runInTransaction(s -> s.find(Product.class, pid).setPrice(new BigDecimal("999.99")));
        assertEquals(0, new BigDecimal("999.99").compareTo(findProduct(pid).getPrice()));
    }

    @Test
    void softDeleteHidesProductButKeepsRow() {
        Category c = newCategory("Food", 2);
        Long pid = productsOf(c).get(0).getId();

        HibernateUtil.runInTransaction(s -> s.remove(s.find(Product.class, pid)));

        assertNull(findProduct(pid));
        assertEquals(1, productsOf(c).size());
        long physicalRows = HibernateUtil.query(s -> ((Number) s
                .createNativeQuery("select count(*) from product where id = " + pid + " and deleted = true")
                .getSingleResult()).longValue());
        assertEquals(1, physicalRows);
    }

    // ---------- BONUS: criteria + pagination ----------

    @Test
    void criteriaSearchFiltersByNameAndPrice() {
        newCategory("Shop", 5); // "Shop item 1..5" priced 100..500
        List<Product> result = HibernateUtil.query(s -> App.search(s, "ITEM", new BigDecimal("200"), new BigDecimal("400")));
        assertEquals(3, result.size());
        assertEquals(0, new BigDecimal("200.00").compareTo(result.get(0).getPrice())); // ordered by price
        assertEquals(5, HibernateUtil.query(s -> App.search(s, null, null, null)).size());
    }

    @Test
    void paginationReturnsCorrectSlices() {
        newCategory("Bulk", 25);
        assertEquals(10, HibernateUtil.query(s -> App.findPage(s, 0, 10)).size());
        assertEquals(5, HibernateUtil.query(s -> App.findPage(s, 2, 10)).size());
        assertEquals(25L, HibernateUtil.query(App::countProducts));
    }
}
