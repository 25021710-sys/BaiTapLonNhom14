package com.auction.common.request;

import java.io.Serializable;

public class UpdateProfileRequest implements Serializable {
    private int userId;
    private String username;
    private String description;
    private String location;
    private String password;

    public UpdateProfileRequest() {}

    public UpdateProfileRequest(int userId, String username,
                                String description, String location,
                                String password) {
        this.userId = userId;
        this.username = username;
        this.description = description;
        this.location = location;
        this.password = password;
    }

    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public String getPassword() { return password; }
}