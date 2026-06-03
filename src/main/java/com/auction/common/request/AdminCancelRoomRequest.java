package com.auction.common.request;

import java.io.Serial;
import java.io.Serializable;

/**
 * Request để Admin HỦY một phòng đấu giá.
 * Khác với adminCloseRoom (kết thúc sớm, có winner):
 * - Status → CANCELED
 * - Không có người thắng
 * - Dùng khi item vi phạm, gian lận, hoặc có vấn đề nghiêm trọng
 */
public class AdminCancelRoomRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private int auctionId;
    private String reason;

    public AdminCancelRoomRequest() {}

    public AdminCancelRoomRequest(int auctionId, String reason) {
        this.auctionId = auctionId;
        this.reason = reason;
    }

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}