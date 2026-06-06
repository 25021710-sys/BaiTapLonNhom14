package com.auction.server.service;

import com.auction.common.dto.DepositRecord;
import com.auction.common.dto.UserDTO;
import com.auction.common.request.BalanceRequest;
import com.auction.common.request.RegisterRequest;
import com.auction.common.request.UpdateProfileRequest;
import com.auction.common.response.BalanceResponse;
import com.auction.common.response.RegisterResponse;
import com.auction.common.response.UpdateProfileResponse;
import com.auction.server.dao.UserDAO;
import com.auction.server.model.User;
import com.auction.common.request.LoginRequest;
import com.auction.common.response.LoginResponse;

import java.sql.SQLException;
import java.util.List;

public class AuthService {
    private UserDAO userDAO;
    public AuthService() {
        this.userDAO = new UserDAO();
    }

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
    public UpdateProfileResponse updateProfile(UpdateProfileRequest req) throws SQLException {

        User user = userDAO.findById(req.getUserId());

        if (user == null) {
            return new UpdateProfileResponse(false, "User không tồn tại", null);
        }

        user.setUsername(req.getUsername());
        user.setDescription(req.getDescription());
        user.setLocation(req.getLocation());

        userDAO.updateProfile(user);

        if (req.getPassword() != null && !req.getPassword().isEmpty()) {
            userDAO.updatePassword(user.getId(), req.getPassword());
        }

        UserDTO dto = mapToDTO(user);

        return new UpdateProfileResponse(true, "Cập nhật thành công", dto);
    }
    public BalanceResponse handleBalance(BalanceRequest req) {
        try {
            User user = userDAO.findById(req.getUserId());

            if (user == null) {
                return new BalanceResponse(false, "User không tồn tại", null);
            }

            if (req.getType().equals("DEPOSIT")) {
                user.deposit(req.getAmount());
            } else {
                user.withdraw(req.getAmount());
            }

            userDAO.updateBalance(user.getId(), user.getBalance());
            // Lưu lịch sử giao dịch vào DB
            userDAO.saveDepositHistory(user.getId(), req.getType(), req.getAmount());

            UserDTO dto = new UserDTO(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getBalance(),
                    user.getRole().name(),
                    user.getCreatedAt(),
                    user.getLocation(),
                    user.getDescription()
            );

            return new BalanceResponse(true, "Thành công", dto);

        } catch (Exception e) {
            return new BalanceResponse(false, e.getMessage(), null);
        }
    }

    /**
     * Lấy lịch sử nạp/rút của user — được gọi khi mở trang Balance.
     */
    public BalanceResponse getDepositHistory(int userId) {
        try {
            User user = userDAO.findById(userId);
            if (user == null) return new BalanceResponse(false, "User không tồn tại", null);

            List<DepositRecord> history = userDAO.getDepositHistory(userId);
            UserDTO dto = mapToDTO(user);
            return new BalanceResponse(true, "OK", dto, history);
        } catch (Exception e) {
            return new BalanceResponse(false, e.getMessage(), null);
        }
    }

    private UserDTO mapToDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getBalance(),
                user.getRole().name(),
                user.getCreatedAt(),
                user.getLocation(),
                user.getDescription()
        );
    }


}