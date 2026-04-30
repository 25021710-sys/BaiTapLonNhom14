package com.auction.common.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public class UserDTO implements Serializable {
    private int id;
    private String username;
    private String email;
    private BigDecimal balance;
    private String role;

    public UserDTO(int id, String username, String email, BigDecimal balance, String role) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.balance = balance;
        this.role = role;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public BigDecimal getBalance() { return balance; }
    public String getRole() { return role; }
}