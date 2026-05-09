package com.auction.common.response;

import com.auction.common.dto.AdminAuctionRequestDTO;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Response trả về danh sách auction request
 * cho màn hình admin approval
 */
public class GetPendingAuctionRequestsResponse
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    // success / fail
    private boolean success;

    // message thông báo
    private String message;

    // danh sách request
    private List<AdminAuctionRequestDTO> requests = new ArrayList<>();

    public GetPendingAuctionRequestsResponse() {}

    public GetPendingAuctionRequestsResponse(
            boolean success,
            String message,
            List<AdminAuctionRequestDTO> requests) {
        this.success = success;
        this.message = message;
        this.requests = requests;
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

    public List<AdminAuctionRequestDTO> getRequests() {
        return requests;
    }

    public void setRequests(List<AdminAuctionRequestDTO> requests) {
        this.requests = requests;
    }
}