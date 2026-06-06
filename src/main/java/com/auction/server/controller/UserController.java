package com.auction.server.controller;

import com.auction.common.dto.UserDTO;
import com.auction.common.request.*;
import com.auction.common.response.*;
import com.auction.server.model.User;
import com.auction.server.service.AuthService;
import com.auction.server.session.ServerSession;
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
 * Nhận ServerSession để ghi nhận trạng thái đăng nhập của kết nối này.
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
    private final ServerSession session;

    public UserController(ServerSession session) {
        this.session = session;
    }

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
                case "USER_GET_PROFILE_BY_USERNAME" -> handleGetProfileByUsername(in, out);
                case "USER_RESOLVE_USERNAMES" -> handleResolveUsernames(in, out);
                case "USER_BALANCE_HISTORY" -> handleBalanceHistory(in, out);
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
     * Nếu thành công, lưu user vào ServerSession của kết nối này.
     */
    private void handleLogin(ObjectInputStream in, ObjectOutputStream out) throws Exception {
        LoginRequest req = (LoginRequest) in.readObject();
        log.info("Xử lý đăng nhập: email={}", req.getEmail());

        LoginResponse res = authService.login(req);

        if (res.isSuccess() && res.getUser() != null) {
            // Ghi nhận đăng nhập vào session của kết nối này
            session.login(res.getUser());
            log.info("Đăng nhập thành công: {} ({})", res.getUser().getUsername(), session);
        } else {
            log.info("Đăng nhập thất bại [{}]: {}", req.getEmail(), res.getMessage());
        }

        out.writeObject(res);
        out.flush();
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
     * Nếu thành công, cập nhật lại thông tin trong session.
     */
    private void handleUpdateProfile(ObjectInputStream in, ObjectOutputStream out) throws Exception {
        UpdateProfileRequest req = (UpdateProfileRequest) in.readObject();
        log.info("Xử lý cập nhật profile: userId={}", req.getUserId());

        // Kiểm tra quyền: chỉ được cập nhật profile của chính mình
        if (session.isLoggedIn() && session.getUserId() != req.getUserId()) {
            log.warn("Từ chối cập nhật profile: session user={} nhưng request userId={}",
                    session.getUserId(), req.getUserId());
            out.writeObject(new UpdateProfileResponse(false, "Không có quyền cập nhật profile của user khác", null));
            out.flush();
            return;
        }

        UpdateProfileResponse res = authService.updateProfile(req);

        if (res.isSuccess() && res.getUser() != null) {
            // Cập nhật lại session với thông tin mới
            session.updateUser(res.getUser());
            log.info("Cập nhật session sau update profile: {}", session);
        }

        out.writeObject(res);
        out.flush();

        log.info("Kết quả update profile [userId={}]: {}", req.getUserId(),
                res.isSuccess() ? "OK" : res.getMessage());
    }

    /**
     * Nạp/rút tiền: đọc BalanceRequest → xử lý → ghi BalanceResponse.
     * Nếu thành công, cập nhật lại balance trong session.
     */
    private void handleBalance(ObjectInputStream in, ObjectOutputStream out) throws Exception {
        BalanceRequest req = (BalanceRequest) in.readObject();
        log.info("Xử lý {} tiền: userId={}, amount={}", req.getType(), req.getUserId(), req.getAmount());

        BalanceResponse res = authService.handleBalance(req);

        if (res.isSuccess() && res.getData() != null) {
            // Cập nhật lại session với balance mới
            session.updateUser(res.getData());
            log.info("Cập nhật balance trong session: userId={}, balance={}",
                    session.getUserId(), res.getData().getBalance());
        }

        out.writeObject(res);
        out.flush();

        log.info("Kết quả balance [userId={}]: {}", req.getUserId(),
                res.isSuccess() ? "Thành công" : res.getMessage());
    }

    private void handleGetProfileByUsername(ObjectInputStream in, ObjectOutputStream out) throws Exception {
        String username = (String) in.readObject();

        com.auction.server.dao.UserDAO userDAO = new com.auction.server.dao.UserDAO();
        User user = userDAO.findByUsername(username);

        if (user == null) {
            out.writeObject(new SimpleResponse(false, "Khong tim thay user"));
            out.flush();
            return;
        }

        com.auction.server.dao.ItemDAO itemDAO = new com.auction.server.dao.ItemDAO();
        int itemCount = itemDAO.countBySeller(user.getId());

        UserDTO dto = new UserDTO(
            user.getId(),
            user.getUsername(),
            null,
            null,
            user.getRole().name(),
            user.getCreatedAt(),
            user.getLocation(),
            user.getDescription()
        );

        out.writeObject(new GetUserProfileResponse(true, "OK", dto, itemCount));
        out.flush();
    }

    private void handleResolveUsernames(ObjectInputStream in, ObjectOutputStream out) throws Exception {
        java.util.List<Integer> ids = (java.util.List<Integer>) in.readObject();
        java.util.Map<Integer, String> result =
            new com.auction.server.dao.UserDAO().findUsernamesByIds(new java.util.HashSet<>(ids));
        out.writeObject(result);
        out.flush();
    }
    private void handleBalanceHistory(ObjectInputStream in, ObjectOutputStream out) throws Exception {
        DepositHistoryRequest req = (DepositHistoryRequest) in.readObject();
        BalanceResponse res = authService.getDepositHistory(req.getUserId());
        out.writeObject(res);
        out.flush();
    }
}
