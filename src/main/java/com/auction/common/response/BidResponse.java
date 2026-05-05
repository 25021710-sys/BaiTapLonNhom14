package com.auction.common.response;

import java.io.Serializable;
import java.math.BigDecimal;

public class BidResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean success;
    private String message;
    private BigDecimal currentHighestBid;

    public BidResponse() {
    }

    public BidResponse(boolean success, String message, BigDecimal currentHighestBid) {
        this.success = success;
        this.message = message;
        this.currentHighestBid = currentHighestBid;
    }

    public boolean isSuccess() {
        return success;
    }
    public void setSuccess(boolean success) {
        this.success = success;
    }
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
    public BigDecimal getCurrentHighestBid() {
        return currentHighestBid;
    }
    public void setCurrentHighestBid(BigDecimal currentHighestBid) {
        this.currentHighestBid = currentHighestBid;
    }
}
