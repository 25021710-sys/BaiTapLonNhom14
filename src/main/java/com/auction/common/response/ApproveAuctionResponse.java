package com.auction.common.response;

import java.io.Serializable;

public class ApproveAuctionResponse
        implements Serializable {

    private boolean success;

    private String message;

    public ApproveAuctionResponse() {
    }

    public ApproveAuctionResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
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
}