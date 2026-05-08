package com.auction.common.request;

import java.io.Serial;
import java.io.Serializable;

/**
 * Request lấy danh sách auction cần admin duyệt
 */
public class GetPendingAuctionRequestsRequest
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Có thể dùng sau này nếu muốn filter
    private String status;

    public GetPendingAuctionRequestsRequest() {
    }

    public GetPendingAuctionRequestsRequest(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}