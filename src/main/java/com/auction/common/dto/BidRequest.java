package com.auction.common.dto;

public class BidRequest {
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
