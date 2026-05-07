package com.auction.common.response;

import com.auction.common.dto.AuctionDTO;

import java.io.Serializable;

public class CreateAuctionResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean success;
    private String message;
    private AuctionDTO auction;

    public CreateAuctionResponse() {}

    public CreateAuctionResponse(boolean success, String message, AuctionDTO auction) {
        this.success = success;
        this.message = message;
        this.auction = auction;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public AuctionDTO getAuction() { return auction; }
    public void setAuction(AuctionDTO auction) { this.auction = auction; }
}
