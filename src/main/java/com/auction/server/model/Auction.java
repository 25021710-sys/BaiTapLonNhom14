package com.auction.server.model;

import java.time.LocalDateTime;

public class Auction extends Entity {
    private int itemId;
    private int sellerId;
    private int highestBidderId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private double currentHighestBid;
    private String status; // OPEN, RUNNING, FINISHED, CANCELLED
    private int extensionCount;

    // 1. Hàm khởi tạo trống
    public Auction() {
        super();
        this.status = "OPEN";
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
        this.status = status;
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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getExtensionCount() { return extensionCount; }
    public void setExtensionCount(int extensionCount) { this.extensionCount = extensionCount; }

    @Override
    public void printInfo() {
        System.out.println("Phiên đấu giá (Item ID: " + itemId + ") | Trạng thái: " + status
                + " | Giá cao nhất hiện tại: " + currentHighestBid);
    }
}