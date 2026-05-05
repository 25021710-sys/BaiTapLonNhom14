package com.auction.common.request;

import java.io.Serializable;
import java.math.BigDecimal;

public class BidRequest implements Serializable {
    // Tránh lỗi khi chạy khac pban java
    private static final long serialVersionUID = 1L;

    private int userId; // id người đặt cược
    private String auctionId;
    private BigDecimal amount;

    public BidRequest(){
    }

    public BidRequest(int userId, String auctionId, BigDecimal amount){
        this.userId = userId;
        this.auctionId = auctionId;
        this.amount = amount;
    }
    public int getUserId() {
        return userId;
    }
    public void setUserId(int userId) {
        this.userId = userId;
    }
    public String getAuctionId(){
        return auctionId;
    }
    public void setAuctionId(String auctionId){
        this.auctionId=auctionId;
    }
    public BigDecimal getAmount() {
        return amount;
    }
    public void setAmount(BigDecimal bidAmount) {
        this.amount = bidAmount;
    }
}
