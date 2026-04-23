package com.auction.server.model;

import java.time.LocalDateTime;

public class User extends Entity {
    private String username;
    private String passwordHash;
    private String email;
    private double balance;      // Dùng khi người này mua (Bidder)
    private String role;         // Phân quyền chính: ADMIN, USER
    private boolean active;

    // Các trạng thái hoạt động thực tế của User
    public enum UserStatus { IDLE, BIDDING, SELLING }
    private UserStatus status = UserStatus.IDLE;

    public User(String username, String passwordHash, String email) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
    }

    public User() {
        super();
        this.active = true;
        this.balance = 0.0;
        this.role = "USER"; // Mặc định mọi người đều là USER thường
    }

    public User(int id, String username, String passwordHash, String email, double balance, String role, boolean active, LocalDateTime createdAt) {
        super(id, createdAt);
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.balance = balance;
        this.role = role;
        this.active = active;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    // Logic nghiệp vụ bổ sung
    public void deposit(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Lỗi: Số tiền nạp phải lớn hơn 0!");
        }
        this.balance += amount;
    }

    public void withdraw(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Lỗi: Số tiền rút phải lớn hơn 0!");
        }
        if (this.balance < amount) {
            throw new IllegalStateException("Lỗi: Số dư không đủ! (Bạn đang có: " + this.balance + ")");
        }
        this.balance -= amount;
    }

    @Override
    public void printInfo() {
        System.out.println("User: " + username + " | Balance: " + balance);
    }
}