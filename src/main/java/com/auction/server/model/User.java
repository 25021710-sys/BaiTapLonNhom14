package com.auction.server.model;

import java.time.LocalDateTime;
import java.math.BigDecimal;

public class User extends Entity {
    private String username;
    private String passwordHash;
    private String email;
    private BigDecimal balance;
    private UserRole role;         // Phân quyền chính: ADMIN, USER
    private boolean active;
    private String salt;
    private String description;
    private String location;

    // Các trạng thái hoạt động thực tế của User

    public User(String username, String passwordHash, String email) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
    }

    public User() {
        super();
        this.active = true;
        this.balance = BigDecimal.valueOf(0);
        this.role = UserRole.USER; // Mặc định mọi người đều là USER thường
        this.description = "";
        this.location = "";
    }

    public User(int id, String username, String passwordHash, String email, BigDecimal balance, UserRole role, boolean active, LocalDateTime createdAt, String salt, String description, String location) {
        super(id, createdAt);
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.balance = balance;
        this.role = role;
        this.active = active;
        this.salt = salt;
        this.description = description;
        this.location = location;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public UserRole getRole() { return role; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    // Logic nghiệp vụ bổ sung
    public void deposit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Lỗi: Số tiền nạp phải lớn hơn 0!");
        }
        this.balance = this.balance.add(amount);
    }

    public void withdraw(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Lỗi: Số tiền rút phải lớn hơn 0!");
        }
        if (this.balance.compareTo(amount) < 0) {
            throw new IllegalStateException("Lỗi: Số dư không đủ! (Bạn đang có: " + this.balance + ")");
        }
        this.balance = this.balance.subtract(amount);
    }

    @Override
    public void printInfo() {
        System.out.println("User: " + username +
                " | Role: " + role +
                " | Balance: " + balance);
    }

}