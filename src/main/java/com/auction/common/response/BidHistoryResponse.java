package com.auction.common.response;

import com.auction.server.model.BidTransaction;
import java.io.Serializable;
import java.util.List;

public class BidHistoryResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    private boolean success;
    private String message;
    private List<BidTransaction> bids;

    public BidHistoryResponse() {}
    public BidHistoryResponse(boolean success, String message, List<BidTransaction> bids) {
        this.success = success; this.message = message; this.bids = bids;
    }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<BidTransaction> getBids() { return bids; }
    public void setBids(List<BidTransaction> bids) { this.bids = bids; }
}