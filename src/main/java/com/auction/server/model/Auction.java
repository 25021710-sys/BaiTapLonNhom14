package com.auction.server.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Auction extends Entity {
    private int itemId;
    private int sellerId;
    private int highestBidderId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal startingPrice;
    private BigDecimal currentPrice;
    private BigDecimal reservePrice;
    private BigDecimal minBidIncrement;
    private AuctionStatus status; // OPEN, RUNNING, FINISHED, CANCELLED
    private int extensionCount;
    private int totalBids;


    // 1. Hàm khởi tạo trống
    public Auction() {
        super();
        this.status = AuctionStatus.OPEN;
        this.extensionCount = 0;
    }

    // 2. Hàm khởi tạo đầy đủ
    public Auction(
            int id,
            LocalDateTime createdAt,
            int itemId,
            int sellerId,
            int highestBidderId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            AuctionStatus status,
            int extensionCount
    ) {

        super(id, createdAt);

        this.itemId = itemId;
        this.sellerId = sellerId;
        this.highestBidderId = highestBidderId;

        this.startTime = startTime;
        this.endTime = endTime;

        this.status = status;

        this.extensionCount = extensionCount;
    }
    public Auction(int itemId, int sellerId, BigDecimal startingPrice, LocalDateTime startTime, LocalDateTime endTime) {
        this.itemId = itemId;
        this.sellerId = sellerId;
        this.startingPrice = startingPrice;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = AuctionStatus.OPEN;
        this.totalBids = 0;
        this.extensionCount = 0;
        this.minBidIncrement = new BigDecimal("1000");
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

    public AuctionStatus getStatus() { return status; }
    public void setStatus(AuctionStatus status) { this.status = status; }

    public int getExtensionCount() { return extensionCount; }
    public void setExtensionCount(int extensionCount) { this.extensionCount = extensionCount; }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }
    public void setStartingPrice(BigDecimal startingPrice) {
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

    public BigDecimal getMinBidIncrement() {
        return minBidIncrement != null ? minBidIncrement : new BigDecimal("1000");
    }
    public void setMinBidIncrement(BigDecimal minBidIncrement) {
        this.minBidIncrement = minBidIncrement;
    }

    @Override
    public void printInfo() {
        System.out.printf(
                "Phiên đấu giá (Item ID: %d) | Trạng thái: %s | Giá hiện tại: %s | Thời điểm tạo: %s%n",
                itemId,
                status,
                currentPrice,
                createdAt
        );
    }
}