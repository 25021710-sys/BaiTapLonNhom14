package com.auction.common.request;

import java.io.Serializable;

/**
 * Request để Admin lấy chi tiết một phòng cụ thể:
 * kèm danh sách người tham gia + 10 bid log gần nhất.
 */
public class AdminGetRoomDetailRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private int auctionId;

    public AdminGetRoomDetailRequest() {}

    public AdminGetRoomDetailRequest(int auctionId) {
        this.auctionId = auctionId;
    }

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }
}