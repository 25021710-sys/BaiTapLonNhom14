package com.auction.server.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BidTransaction extends Entity {
    private int auctionId;
    private int bidderId;
    private BigDecimal amount;

    // 1. Hàm khởi tạo trống
    public BidTransaction() {
        super();
    }

    // 2. Hàm khởi tạo đầy đủ
    public BidTransaction(int id, LocalDateTime createdAt, int auctionId, int bidderId, BigDecimal amount) {
        super(id, createdAt);
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amount = amount;
    }

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

    public int getBidderId() { return bidderId; }
    public void setBidderId(int bidderId) { this.bidderId = bidderId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    @Override
    public void printInfo() {
        System.out.println("[MANUAL] User ID " + bidderId + " đặt giá " + amount + " cho Phiên " + auctionId + " lúc " + this.getCreatedAt());
    }
}