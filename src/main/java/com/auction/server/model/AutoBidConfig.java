/*package com.auction.server.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AutoBidConfig implements Serializable,Comparable<AutoBidConfig> {
    private int auctionId;
    private int bidderId;
    private String bidderUsername;
    private BigDecimal maxBid;
    private BigDecimal increment;
    private LocalDateTime registeredAt;
    private boolean active;

    // 1. Hàm khởi tạo trống
    public AutoBidConfig() {
        this.registeredAt = LocalDateTime.now();
        this.active = true;
    }

    // 2. Hàm khởi tạo đầy đủ tham số
    public AutoBidConfig(int bidderId, String bidderUsername,int auctionId, BigDecimal maxBid, BigDecimal increment) {
        this.bidderId = bidderId;
        this.bidderUsername=bidderUsername;
        this.auctionId=auctionId;
        this.maxBid = maxBid;
        this.increment = increment;
        this.registeredAt = LocalDateTime.now();
        this.active = true;
    }
    public int compareTo(AutoBidConfig other) {
        int cmp = other.maxBid.compareTo(this.maxBid);
        return cmp != 0 ? cmp : this.registeredAt.compareTo(other.registeredAt);
    }

    public BigDecimal calculateNextBid(BigDecimal currentPrice) {
        BigDecimal nextBid = currentPrice.add(this.increment);
        if (nextBid.compareTo(this.maxBid) > 0) {
            return this.maxBid.compareTo(currentPrice) > 0 ? this.maxBid : null;
        } else {
            return nextBid;
        }
    }

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

    public int getBidderId() { return bidderId; }
    public void setBidderId(int bidderId) { this.bidderId = bidderId; }

    public BigDecimal getMaxBid() { return maxBid; }
    public void setMaxBid(BigDecimal maxBid) { this.maxBid = maxBid; }

    public BigDecimal getIncrement() { return increment; }
    public void setIncrement(BigDecimal increment) { this.increment = increment; }

    public String getBidderUsername(){
        return bidderUsername;
    }
    public void setBidderUsername(String bidderUsername) {
        this.bidderUsername = bidderUsername;
    }

    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isActive() {
        return active;
    }

    public void printInfo() {
        System.out.println("[AUTO-BID] Người dùng ID: " + bidderId
                + " | Tự động đặt giá cho Phiên: " + auctionId
                + " | Giới hạn tối đa: " + maxBid
                + " | Bước giá: " + increment);
    }

}*/