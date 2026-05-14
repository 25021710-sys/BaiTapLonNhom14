package com.auction.common.request;

import java.io.Serializable;

/**
 * Request để Admin đóng cưỡng bức một phòng đấu giá (kết thúc sớm).
 * Server sẽ đặt trạng thái phòng về CANCELED, broadcast kết thúc cho tất cả subscriber.
 */
public class AdminCloseRoomRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private int auctionId;
    private String reason; // Lý do đóng (ghi vào log và broadcast cho client)

    public AdminCloseRoomRequest() {}

    public AdminCloseRoomRequest(int auctionId, String reason) {
        this.auctionId = auctionId;
        this.reason = reason;
    }

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}