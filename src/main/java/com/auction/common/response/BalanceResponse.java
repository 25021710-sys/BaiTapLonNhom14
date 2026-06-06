package com.auction.common.response;

import com.auction.common.dto.DepositRecord;
import com.auction.common.dto.UserDTO;
import java.io.Serializable;
import java.util.List;

public class BalanceResponse implements Serializable {
    private boolean success;
    private String message;
    private UserDTO user;
    private List<DepositRecord> history; // lịch sử nạp/rút — null nếu là response của DEPOSIT/WITHDRAW

    // Constructor cũ — dùng cho deposit/withdraw (không cần trả history)
    public BalanceResponse(boolean success, String message, UserDTO user) {
        this.success = success;
        this.message = message;
        this.user    = user;
    }

    // Constructor mới — dùng khi trả lịch sử (USER_BALANCE_HISTORY)
    public BalanceResponse(boolean success, String message, UserDTO user, List<DepositRecord> history) {
        this.success = success;
        this.message = message;
        this.user    = user;
        this.history = history;
    }

    public boolean isSuccess()              { return success; }
    public String getMessage()              { return message; }
    public UserDTO getData()                { return user; }
    public List<DepositRecord> getHistory() { return history; }
}