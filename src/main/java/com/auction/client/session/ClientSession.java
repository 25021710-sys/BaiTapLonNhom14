package com.auction.client.session;

import com.auction.common.dto.DepositRecord;
import com.auction.common.dto.UserDTO;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientSession {
    private static UserDTO currentUser;

    // Listener để notify tất cả màn hình khi balance thay đổi
    private static final List<Runnable> balanceListeners = new CopyOnWriteArrayList<>();

    // Cache history mới nhất từ server push — BalanceController đọc khi mở
    // null = chưa có push nào; non-null = server vừa push history mới, dùng ngay
    private static volatile List<DepositRecord> pendingHistory = null;

    public static void setCurrentUser(UserDTO user) {
        currentUser = user;
    }
    public static UserDTO getCurrentUser() {
        return currentUser;
    }

    /** Cập nhật balance và notify toàn bộ listener (tất cả AuctionRoom đang mở) */
    public static void updateBalance(BigDecimal newBalance) {
        if (currentUser == null) return;
        currentUser.setBalance(newBalance);
        for (Runnable listener : balanceListeners) {
            listener.run();
        }
    }

    /** Lưu history mới nhất từ server push — BalanceController sẽ đọc khi initialize(). */
    public static void setPendingHistory(List<DepositRecord> history) {
        pendingHistory = history;
    }

    /**
     * Lấy và xóa pending history (consume một lần).
     * Trả về null nếu chưa có push nào.
     */
    public static List<DepositRecord> consumePendingHistory() {
        List<DepositRecord> h = pendingHistory;
        pendingHistory = null;
        return h;
    }

    /** Đăng ký callback khi balance thay đổi, gọi removeBalanceListener khi màn hình đóng. */
    public static void addBalanceListener(Runnable listener) {
        if (listener != null) balanceListeners.add(listener);
    }

    public static void removeBalanceListener(Runnable listener) {
        balanceListeners.remove(listener);
    }

    public static void clear() {
        currentUser = null;
        balanceListeners.clear();
        pendingHistory = null;
    }
}