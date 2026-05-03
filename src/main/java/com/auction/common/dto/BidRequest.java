package com.auction.common.dto;

import java.io.Serializable; // Nhớ import cái này

public class BidRequest implements Serializable {
    private String auctionId;
    private double amount;
    public BidRequest(String auctionId,double amount){
        this.auctionId = auctionId;
        this.amount = amount;
    }
    public String getAuctionId(){
        return auctionId;
    }
    public void setAuctionId(String auctionId){
        this.auctionId=auctionId;
    }
    public double getAmount(){
        return amount;
    }
}
