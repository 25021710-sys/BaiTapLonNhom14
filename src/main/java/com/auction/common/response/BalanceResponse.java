package com.auction.common.response;

import com.auction.common.dto.UserDTO;
import java.io.Serializable;

public class BalanceResponse implements Serializable {
    private boolean success;
    private String message;
    private UserDTO user;

    public BalanceResponse(boolean success, String message, UserDTO user) {
        this.success = success;
        this.message = message;
        this.user = user;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public UserDTO getData() { return user; }
}