package com.auction.common.request;

import java.io.Serializable;

public class RegisterRequest implements Serializable {
    private String username;
    private String email;
    private String password;

    public RegisterRequest() {}

    public RegisterRequest(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }

    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
}
