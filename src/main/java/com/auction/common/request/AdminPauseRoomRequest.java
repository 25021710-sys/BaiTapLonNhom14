package com.auction.common.request;

import java.io.Serializable;

/**
 * Request để Admin tạm dừng một phòng đấu giá đang RUNNING.
 * Server sẽ đặt trạng thái phòng về OPEN (tạm dừng) và broadcast cho tất cả subscriber.
 */
public class AdminPauseRoomRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private int auctionId;
    private String reason; // Lý do tạm dừng (hiển thị cho người dùng)

    public AdminPauseRoomRequest() {}

    public AdminPauseRoomRequest(int auctionId, String reason) {
        this.auctionId = auctionId;
        this.reason = reason;
    }

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}