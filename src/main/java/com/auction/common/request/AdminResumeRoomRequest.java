package com.auction.common.request;

import java.io.Serializable;

/**
 * Request để Admin tiếp tục một phòng đấu giá đang bị tạm dừng (OPEN).
 * Server sẽ đặt trạng thái phòng về RUNNING và broadcast cho tất cả subscriber.
 */
public class AdminResumeRoomRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private int auctionId;

    public AdminResumeRoomRequest() {}

    public AdminResumeRoomRequest(int auctionId) {
        this.auctionId = auctionId;
    }

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }
}