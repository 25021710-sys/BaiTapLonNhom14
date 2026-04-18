
package com.auction.server.model;

import java.time.LocalDateTime;

public class AutoBidConfig extends Entity {
    private int auctionId;
    private int bidderId;
    private double maxBid;
    private double increment;

    // 1. Hàm khởi tạo trống
    public AutoBidConfig() {
        super();
    }

    // 2. Hàm khởi tạo đầy đủ tham số
    public AutoBidConfig(int id, LocalDateTime createdAt, int auctionId, int bidderId, double maxBid, double increment) {
        super(id, createdAt);
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.maxBid = maxBid;
        this.increment = increment;
    }

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

    public int getBidderId() { return bidderId; }
    public void setBidderId(int bidderId) { this.bidderId = bidderId; }

    public double getMaxBid() { return maxBid; }
    public void setMaxBid(double maxBid) { this.maxBid = maxBid; }

    public double getIncrement() { return increment; }
    public void setIncrement(double increment) { this.increment = increment; }

    @Override
    public void printInfo() {
        System.out.println("[AUTO-BID] Người dùng ID: " + bidderId
                + " | Tự động đặt giá cho Phiên: " + auctionId
                + " | Giới hạn tối đa: " + maxBid
                + " | Bước giá: " + increment);
    }
}