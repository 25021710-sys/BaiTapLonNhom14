package com.auction.client.session;

import com.auction.common.dto.UserDTO;

public class ClientSession {
    private static UserDTO currentUser;

    public static void setCurrentUser(UserDTO user) {
        currentUser = user;
    }

    public static UserDTO getCurrentUser() {
        return currentUser;
    }

    // (optional) logout
    public static void clear() {
        currentUser = null;
    }
}