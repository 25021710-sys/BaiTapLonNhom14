package com.auction.server.model;

import java.time.LocalDateTime;

public class User extends Entity {
    private String username;
    private String passwordHash;
    private String email;
    private String fullName;
    private double balance;      // Dùng khi người này mua (Bidder)
    private String shopName;     // Dùng khi người này bán (Seller)
    private String role;         // Phân quyền chính: ADMIN, USER
    private boolean active;

    // Các trạng thái hoạt động thực tế của User
    public enum UserStatus { IDLE, BIDDING, SELLING }
    private UserStatus status = UserStatus.IDLE;

    public User() {
        super();
        this.active = true;
        this.balance = 0.0;
        this.role = "USER"; // Mặc định mọi người đều là USER thường
    }

    public User(int id, LocalDateTime createdAt, String username, String passwordHash, String email, String fullName, double balance, String shopName, String role, boolean active) {
        super(id, createdAt);
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.fullName = fullName;
        this.balance = balance;
        this.shopName = shopName;
        this.role = role;
        this.active = active;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    // Logic nghiệp vụ bổ sung
    public void deposit(double amount) { this.balance += amount; }
    public boolean withdraw(double amount) {
        if (this.balance >= amount) {
            this.balance -= amount;
            return true;
        }
        return false;
    }

    @Override
    public void printInfo() {
        System.out.println("User: " + username + " | Balance: " + balance + " | Shop: " + (shopName != null ? shopName : "N/A"));
    }
}