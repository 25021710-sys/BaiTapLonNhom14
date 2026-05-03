package com.auction.server.controller;

import com.auction.common.request.BalanceRequest;
import com.auction.common.request.LoginRequest;
import com.auction.common.request.RegisterRequest;
import com.auction.common.request.UpdateProfileRequest;
import com.auction.common.response.BalanceResponse;
import com.auction.common.response.LoginResponse;
import com.auction.common.response.RegisterResponse;
import com.auction.common.response.UpdateProfileResponse;
import com.auction.server.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * UserController - xử lý tất cả action có tiền tố "USER_".
 *
 * Giao thức socket:
 *   Client gửi: action (String) → request object
 *   Server trả: response object
 *
 * Các action được hỗ trợ:
 *   USER_LOGIN           → LoginRequest    → LoginResponse
 *   USER_REGISTER        → RegisterRequest → RegisterResponse
 *   USER_UPDATE_PROFILE  → UpdateProfileRequest → UpdateProfileResponse
 *   USER_BALANCE         → BalanceRequest  → BalanceResponse
 */
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final AuthService authService = new AuthService();

    /**
     * Điểm vào duy nhất — được gọi từ ClientHandler sau khi đọc action.
     */
    public void processRequest(String action,
                               ObjectInputStream in,
                               ObjectOutputStream out) {
        try {
            switch (action) {
                case "USER_LOGIN"          -> handleLogin(in, out);
                case "USER_REGISTER"       -> handleRegister(in, out);
                case "USER_UPDATE_PROFILE" -> handleUpdateProfile(in, out);
                case "USER_BALANCE"        -> handleBalance(in, out);
                default -> {
                    log.warn("Action USER không hợp lệ: {}", action);
                    out.writeObject(new LoginResponse(false, "Action không hợp lệ: " + action, null));
                    out.flush();
                }
            }
        } catch (Exception e) {
            log.error("Lỗi xử lý action {}: {}", action, e.getMessage(), e);
            try {
                out.writeObject(new LoginResponse(false, "Lỗi server: " + e.getMessage(), null));
                out.flush();
            } catch (Exception ignored) {}
        }
    }

    // ── Handlers ─────────────────────────────────────────────

    /**
     * Đăng nhập: đọc LoginRequest → xử lý → ghi LoginResponse.
     */
    private void handleLogin(ObjectInputStream in, ObjectOutputStream out) throws Exception {
        LoginRequest req = (LoginRequest) in.readObject();
        log.info("Xử lý đăng nhập: email={}", req.getEmail());

        LoginResponse res = authService.login(req);
        out.writeObject(res);
        out.flush();

        log.info("Kết quả đăng nhập [{}]: {}", req.getEmail(), res.isSuccess() ? "OK" : res.getMessage());
    }

    /**
     * Đăng ký: đọc RegisterRequest → xử lý → ghi RegisterResponse.
     */
    private void handleRegister(ObjectInputStream in, ObjectOutputStream out) throws Exception {
        RegisterRequest req = (RegisterRequest) in.readObject();
        log.info("Xử lý đăng ký: username={}", req.getUsername());

        RegisterResponse res = authService.register(req);
        out.writeObject(res);
        out.flush();

        log.info("Kết quả đăng ký [{}]: {}", req.getUsername(), res.isSuccess() ? "OK" : res.getMessage());
    }

    /**
     * Cập nhật profile: đọc UpdateProfileRequest → xử lý → ghi UpdateProfileResponse.
     */
    private void handleUpdateProfile(ObjectInputStream in, ObjectOutputStream out) throws Exception {
        UpdateProfileRequest req = (UpdateProfileRequest) in.readObject();
        log.info("Xử lý cập nhật profile: userId={}", req.getUserId());

        UpdateProfileResponse res = authService.updateProfile(req);
        out.writeObject(res);
        out.flush();

        log.info("Kết quả update profile [userId={}]: {}", req.getUserId(),
                res.isSuccess() ? "OK" : res.getMessage());
    }

    /**
     * Nạp/rút tiền: đọc BalanceRequest → xử lý → ghi BalanceResponse.
     */
    private void handleBalance(ObjectInputStream in, ObjectOutputStream out) throws Exception {
        BalanceRequest req = (BalanceRequest) in.readObject();
        log.info("Xử lý {} tiền: userId={}, amount={}", req.getType(), req.getUserId(), req.getAmount());

        BalanceResponse res = authService.handleBalance(req);
        out.writeObject(res);
        out.flush();

        log.info("Kết quả balance [userId={}]: {}", req.getUserId(),
                res.isSuccess() ? "Thành công" : res.getMessage());
    }
}
