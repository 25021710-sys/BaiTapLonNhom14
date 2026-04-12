package com.auction.server.model;

public class User{
    protected String userId;
    protected String username;
    protected String passwordHash;
    protected String fullName;
    protected String email;
    protected double balance;
    public enum UserStatus { IDLE, BIDDING, SELLING }
    protected UserStatus status = UserStatus.IDLE;

    public User(String userId, String username, String passwordHash, String email, String fullName) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.fullName = fullName;
    }

    public void setUserId(String userId){this.userId = userId;}
    public void setUsername(String username){this.username = username;}
    public void setPasswordHash(String passwordHash){this.passwordHash = passwordHash;}
    public void setFullName(String fullName){this.fullName = fullName;}
    public void setEmail(String email){this.email = email;}

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }

    public void setBalance(double balance){
        this.balance = balance;
    }
}
