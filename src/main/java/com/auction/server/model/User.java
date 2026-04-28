package com.auction.server.model;

import java.time.LocalDateTime;
import java.math.BigDecimal;

public class User extends Entity {
    private String username;
    private String passwordHash;
    private String email;
    private BigDecimal balance;      // Dùng khi người này mua (Bidder)
    private UserRole role;         // Phân quyền chính: ADMIN, USER
    private boolean active;
    private String salt;
    private String des;
    private String location;

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
        this.balance = BigDecimal.ZERO;
        this.role = UserRole.USER; // Mặc định mọi người đều là USER thường// Mặc định mọi người đều là USER thường
        this.des = "";
        this.location = "";
    }

    public User(int id, String username, String passwordHash, String email, BigDecimal balance, UserRole role, boolean active, LocalDateTime createdAt, String salt) {
        super(id, createdAt);
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.balance = balance;
        this.role = role;
        this.active = active;
        this.salt = salt;
        this.des = des;
        this.location = location;
    }
    public boolean canBid(){
        return this.role == UserRole.BIDDER;
    }
    public boolean canSell(){
        return this.role == UserRole.SELLER;
    }
    public boolean canManageSystem(){
        return this.role == UserRole.ADMIN;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) {
        if (status == UserStatus.SELLING && this.role != UserRole.SELLER) {
            throw new IllegalStateException("Chỉ SELLER mới có thể ở trạng thái SELLING");
        }
        if (status == UserStatus.BIDDING && this.role != UserRole.BIDDER) {
            throw new IllegalStateException("Chỉ BIDDER mới có thể ở trạng thái BIDDING");
        }
        this.status = status;
    }

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
                " | Balance: " + balance +
                " | Status: " + status);
    }   

}