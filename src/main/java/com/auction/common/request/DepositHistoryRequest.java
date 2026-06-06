package com.auction.common.request;

import java.io.Serializable;

/**
 * Client gửi lên để lấy lịch sử nạp/rút tiền.
 */
public class DepositHistoryRequest implements Serializable {
    private int userId;

    public DepositHistoryRequest(int userId) {
        this.userId = userId;
    }

    public int getUserId() { return userId; }
}