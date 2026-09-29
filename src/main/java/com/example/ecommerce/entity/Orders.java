package com.example.ecommerce.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@NamedQuery(
        name = "Orders.findWithDetails",
        query = "SELECT DISTINCT o FROM Orders o "
              + "JOIN FETCH o.user "
              + "JOIN FETCH o.orderDetails d "
              + "JOIN FETCH d.product "
              + "WHERE o.id = :id")
public class Orders {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderDetails> orderDetails = new ArrayList<>();

    public Orders() {}

    public void addDetail(OrderDetails d) {
        orderDetails.add(d);
        d.setOrder(this);
    }

    public Long getId() { return id; }
    public LocalDateTime getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDateTime orderDate) { this.orderDate = orderDate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public Users getUser() { return user; }
    public void setUser(Users user) { this.user = user; }
    public List<OrderDetails> getOrderDetails() { return orderDetails; }

    @Override
    public String toString() { return "Orders{id=" + id + ", date=" + orderDate + ", total=" + totalAmount + "}"; }
}
