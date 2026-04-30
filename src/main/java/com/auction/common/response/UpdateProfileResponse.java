package com.auction.common.response;

import com.auction.common.dto.UserDTO;
import java.io.Serializable;

public class UpdateProfileResponse implements Serializable {
    private boolean success;
    private String message;
    private UserDTO user;

    public UpdateProfileResponse() {}

    public UpdateProfileResponse(boolean success, String message, UserDTO user) {
        this.success = success;
        this.message = message;
        this.user = user;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public UserDTO getUser() { return user; }
}