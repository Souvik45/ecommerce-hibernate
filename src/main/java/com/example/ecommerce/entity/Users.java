package com.example.ecommerce.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** Stores a salted PBKDF2 hash, never the raw password (see PasswordUtil). */
    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.CUSTOMER;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Orders> orders = new ArrayList<>();

    public Users() {}

    public Users(String username, String passwordHash, String email, Role role) {
        this.username = username;
        this.password = passwordHash;
        this.email = email;
        this.role = role;
    }

    public void addOrder(Orders o) {
        orders.add(o);
        o.setUser(this);
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public List<Orders> getOrders() { return orders; }

    @Override
    public String toString() { return "Users{id=" + id + ", username='" + username + "', role=" + role + "}"; }
}
