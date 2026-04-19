package com.auction.server.model;

import java.time.LocalDateTime;

public class User extends Entity {
    protected String username;
    protected String passwordHash;
    protected String email;
    protected String fullName;
    protected boolean active;
    protected String role; // ADMIN, SELLER, BIDDER

    public User() { super(); }

    public User(int id, LocalDateTime createdAt, String username, String passwordHash, String email, String fullName, boolean active, String role) {
        super(id, createdAt);
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.fullName = fullName;
        this.active = active;
        this.role = role;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    @Override
    public void printInfo() {
        System.out.println("User: " + username + " - Role: " + role);
    }
}