package com.auction.common.response;

import com.auction.common.dto.AuctionDTO;
import java.io.Serializable;
import java.util.List;

public class AuctionListResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    private boolean success;
    private String message;
    private List<AuctionDTO> auctions;

    public AuctionListResponse() {}
    public AuctionListResponse(boolean success, String message, List<AuctionDTO> auctions) {
        this.success = success; this.message = message; this.auctions = auctions;
    }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<AuctionDTO> getAuctions() { return auctions; }
    public void setAuctions(List<AuctionDTO> auctions) { this.auctions = auctions; }
}