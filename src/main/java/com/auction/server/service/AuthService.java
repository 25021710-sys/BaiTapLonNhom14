package com.auction.server.service;

import com.auction.common.dto.UserDTO;
import com.auction.server.model.User;
import com.auction.common.request.LoginRequest;
import com.auction.common.response.LoginResponse;

public class AuthService {

    public LoginResponse login(LoginRequest req) {
        User user = userDAO.login(req.getEmail(), req.getPassword());

        if (user == null) {
            return new LoginResponse(false, "Sai tài khoản", null);
        }

        UserDTO dto = mapToDTO(user);

        return new LoginResponse(true, "OK", dto);
    }

    private UserDTO mapToDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getBalance(),
                user.getRole().name()
        );
    }
}