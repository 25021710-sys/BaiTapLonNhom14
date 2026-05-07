package com.auction.common.request;

import java.io.Serializable;

public class ApproveAuctionRequest implements Serializable {

    private int requestId;
    private int adminId;

    public ApproveAuctionRequest() {
    }

    public int getRequestId() {
        return requestId;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    public int getAdminId() {
        return adminId;
    }

    public void setAdminId(int adminId) {
        this.adminId = adminId;
    }
}