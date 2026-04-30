package com.auction.common.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public class UserDTO implements Serializable {
    private int id;
    private String username;
    private String email;
    private BigDecimal balance;
    private String role;
    private String location;
    private String description;

    public UserDTO() {}

    public UserDTO(int id, String username, String email,
                   BigDecimal balance, String role,
                   String location, String description) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.balance = balance;
        this.role = role;
        this.location = location;
        this.description = description;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public BigDecimal getBalance() { return balance; }
    public String getRole() { return role; }
    public String getLocation() { return location; }
    public String getDescription() { return description; }

    // 👉 thêm setter để update session dễ hơn
    public void setUsername(String username) { this.username = username; }
    public void setLocation(String location) { this.location = location; }
    public void setDescription(String description) { this.description = description; }
}