package com.auction.server.session;

import com.auction.common.dto.UserDTO;

/**
 * ServerSession - lưu trạng thái đăng nhập của MỘT client kết nối vào server.
 *
 * Mỗi ClientHandler giữ một instance ServerSession riêng.
 * Session này theo dõi:
 *   - User hiện tại đã đăng nhập (null nếu chưa đăng nhập)
 *   - Thời điểm đăng nhập
 *   - Trạng thái xác thực
 *
 * Thiết kế: KHÔNG dùng static (khác với ClientSession bên client),
 * vì server cần quản lý NHIỀU session song song (một per connection).
 */
public class ServerSession {

    private UserDTO loggedInUser;
    private long loginTimeMillis;

    public ServerSession() {
        this.loggedInUser = null;
        this.loginTimeMillis = 0;
    }

    // ── Đăng nhập / Đăng xuất ────────────────────────────────

    /**
     * Ghi nhận user đã đăng nhập thành công.
     */
    public void login(UserDTO user) {
        this.loggedInUser = user;
        this.loginTimeMillis = System.currentTimeMillis();
    }

    /**
     * Xóa thông tin đăng nhập (logout hoặc ngắt kết nối).
     */
    public void logout() {
        this.loggedInUser = null;
        this.loginTimeMillis = 0;
    }

    // ── Truy vấn trạng thái ──────────────────────────────────

    /**
     * Kiểm tra user đã đăng nhập chưa.
     */
    public boolean isLoggedIn() {
        return loggedInUser != null;
    }

    /**
     * Lấy thông tin user đang đăng nhập.
     * @return UserDTO hoặc null nếu chưa đăng nhập.
     */
    public UserDTO getLoggedInUser() {
        return loggedInUser;
    }

    /**
     * Lấy userId của user đang đăng nhập.
     * @return userId hoặc -1 nếu chưa đăng nhập.
     */
    public int getUserId() {
        return loggedInUser != null ? loggedInUser.getId() : -1;
    }

    /**
     * Lấy username của user đang đăng nhập.
     * @return username hoặc "anonymous" nếu chưa đăng nhập.
     */
    public String getUsername() {
        return loggedInUser != null ? loggedInUser.getUsername() : "anonymous";
    }

    /**
     * Kiểm tra user hiện tại có phải ADMIN không.
     */
    public boolean isAdmin() {
        return loggedInUser != null && "ADMIN".equalsIgnoreCase(loggedInUser.getRole());
    }

    /**
     * Cập nhật lại thông tin user trong session (ví dụ sau khi update profile hoặc nạp tiền).
     */
    public void updateUser(UserDTO updatedUser) {
        if (loggedInUser != null && updatedUser != null && loggedInUser.getId() == updatedUser.getId()) {
            this.loggedInUser = updatedUser;
        }
    }

    /**
     * Thời gian đã đăng nhập (ms). 0 nếu chưa đăng nhập.
     */
    public long getSessionDurationMs() {
        if (!isLoggedIn()) return 0;
        return System.currentTimeMillis() - loginTimeMillis;
    }

    @Override
    public String toString() {
        if (!isLoggedIn()) return "ServerSession[anonymous]";
        return String.format("ServerSession[userId=%d, username=%s, role=%s]",
                loggedInUser.getId(), loggedInUser.getUsername(), loggedInUser.getRole());
    }
}