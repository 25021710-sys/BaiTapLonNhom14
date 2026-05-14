package com.auction.common.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AuctionUpdateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum UpdateType {
        BID_PLACED,
        AUCTION_ENDED,
        AUCTION_EXTENDED,
        AUCTION_STARTED,
        PARTICIPANT_CHANGED
    }

    private int auctionId;
    private UpdateType type;
    private BigDecimal newPrice;
    private int highestBidderId;
    private String highestBidderUsername;
    private LocalDateTime newEndTime;
    private String message;
    private int participantCount;

    public AuctionUpdateDTO() {}

    public AuctionUpdateDTO(int auctionId, UpdateType type, BigDecimal newPrice,
                            int highestBidderId, String highestBidderUsername,
                            LocalDateTime newEndTime, String message) {
        this.auctionId = auctionId;
        this.type = type;
        this.newPrice = newPrice;
        this.highestBidderId = highestBidderId;
        this.highestBidderUsername = highestBidderUsername;
        this.newEndTime = newEndTime;
        this.message = message;
    }

    public AuctionUpdateDTO(int auctionId, UpdateType type, BigDecimal newPrice,
                            int highestBidderId, String highestBidderUsername,
                            LocalDateTime newEndTime, String message, int participantCount) {
        this(auctionId, type, newPrice, highestBidderId, highestBidderUsername, newEndTime, message);
        this.participantCount = participantCount;
    }

    public int getParticipantCount() { return participantCount; }
    public void setParticipantCount(int participantCount) { this.participantCount = participantCount; }

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

    public UpdateType getType() { return type; }
    public void setType(UpdateType type) { this.type = type; }

    public BigDecimal getNewPrice() { return newPrice; }
    public void setNewPrice(BigDecimal newPrice) { this.newPrice = newPrice; }

    public int getHighestBidderId() { return highestBidderId; }
    public void setHighestBidderId(int highestBidderId) { this.highestBidderId = highestBidderId; }

    public String getHighestBidderUsername() { return highestBidderUsername; }
    public void setHighestBidderUsername(String highestBidderUsername) { this.highestBidderUsername = highestBidderUsername; }

    public LocalDateTime getNewEndTime() { return newEndTime; }
    public void setNewEndTime(LocalDateTime newEndTime) { this.newEndTime = newEndTime; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}