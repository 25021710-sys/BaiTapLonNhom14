package com.auction.common.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Auction extends Entity {
    private int id; // PK, auto increment
    private int itemId;
    private int sellerId;
    private int highestBidderId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal startingPrice;
    private BigDecimal currentPrice;
    private BigDecimal reservePrice;
    private double currentHighestBid;
    private AuctionStatus status; // OPEN, RUNNING, FINISHED, CANCELLED
    private int extensionCount;
    private LocalDateTime createdAt;

    // 1. Hàm khởi tạo trống
    public Auction() {
        super();
        this.status =AuctionStatus.OPEN;
        this.extensionCount = 0;
    }

    // 2. Hàm khởi tạo đầy đủ
    public Auction(int id, LocalDateTime createdAt, int itemId, int sellerId, int highestBidderId,
                   LocalDateTime startTime, LocalDateTime endTime, double currentHighestBid,
                   String status, int extensionCount) {
        super(id, createdAt);
        this.itemId = itemId;
        this.sellerId = sellerId;
        this.highestBidderId = highestBidderId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.currentHighestBid = currentHighestBid;
        this.status = AuctionStatus.OPEN;
        this.extensionCount = extensionCount;
    }

    public int getItemId() { return itemId; }
    public void setItemId(int itemId) { this.itemId = itemId; }

    public int getSellerId() { return sellerId; }
    public void setSellerId(int sellerId) { this.sellerId = sellerId; }

    public int getHighestBidderId() { return highestBidderId; }
    public void setHighestBidderId(int highestBidderId) { this.highestBidderId = highestBidderId; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public double getCurrentHighestBid() { return currentHighestBid; }
    public void setCurrentHighestBid(double currentHighestBid) { this.currentHighestBid = currentHighestBid; }

    public AuctionStatus getStatus() { return status; }
    public void setStatus(AuctionStatus status) { this.status = status; }

    public int getExtensionCount() { return extensionCount; }
    public void setExtensionCount(int extensionCount) { this.extensionCount = extensionCount; }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }
    public void setStartingPricePrice(BigDecimal startingPrice) {
        this.startingPrice = startingPrice;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }

    public BigDecimal getReservePrice() {
        return reservePrice;
    }
    public void setReservePrice(BigDecimal reservePrice) {
        this.reservePrice = reservePrice;
    }

    @Override
    public void printInfo() {
        System.out.println("Phiên đấu giá (Item ID: " + itemId + ") | Trạng thái: " + status
                + " | Giá cao nhất hiện tại: " + currentHighestBid);
    }
}