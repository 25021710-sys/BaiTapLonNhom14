package com.auction.common.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BidTransaction extends Entity {
    private int auctionId;
    private int bidderId;
    private BigDecimal amount;
    private boolean isAutoBid;

    // 1. Hàm khởi tạo trống
    public BidTransaction() {
        super();
        this.isAutoBid = false;
    }

    // 2. Hàm khởi tạo đầy đủ
    public BidTransaction(int id, LocalDateTime createdAt, int auctionId, int bidderId, BigDecimal amount, boolean isAutoBid) {
        super(id, createdAt);
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amount = amount;
        this.isAutoBid = isAutoBid;
    }

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

    public int getBidderId() { return bidderId; }
    public void setBidderId(int bidderId) { this.bidderId = bidderId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public boolean isAutoBid() { return isAutoBid; }
    public void setAutoBid(boolean autoBid) { this.isAutoBid = autoBid; }

    @Override
    public void printInfo() {
        String type = isAutoBid ? "[AUTO]" : "[MANUAL]";
        System.out.println(type + " User ID " + bidderId + " đặt giá " + amount + " cho Phiên " + auctionId + " lúc " + this.getCreatedAt());
    }
}