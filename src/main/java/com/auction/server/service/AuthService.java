package com.auction.server.service;

import com.auction.common.dto.UserDTO;
import com.auction.common.request.RegisterRequest;
import com.auction.common.response.RegisterResponse;
import com.auction.server.dao.UserDAO;
import com.auction.server.model.User;
import com.auction.common.request.LoginRequest;
import com.auction.common.response.LoginResponse;

import java.sql.SQLException;

public class AuthService {
    private UserDAO userDAO;

    public LoginResponse login(LoginRequest req) throws SQLException {
        User user = userDAO.login(req.getEmail(), req.getPassword());

        if (user == null) {
            return new LoginResponse(false, "Sai tài khoản", null);
        }

        UserDTO dto = mapToDTO(user);

        return new LoginResponse(true, "OK", dto);
    }
    public RegisterResponse register(RegisterRequest req) throws SQLException {

        if (req == null
                || req.getEmail() == null
                || req.getPassword() == null
                || req.getUsername() == null) {
            return new RegisterResponse(false, "Dữ liệu không hợp lệ", null);
        }

        User user = userDAO.register(
                req.getUsername(),
                req.getPassword(),
                req.getEmail()
        );

        if (user == null) {
            return new RegisterResponse(false, "Email đã tồn tại", null);
        }

        UserDTO dto = mapToDTO(user);

        return new RegisterResponse(true, "Đăng ký thành công", dto);
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