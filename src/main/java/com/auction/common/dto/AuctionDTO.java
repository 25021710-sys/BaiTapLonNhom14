package com.auction.common.dto;

import com.auction.server.model.AuctionStatus;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AuctionDTO implements Serializable {
    private static final long serialVersionUID = 3L;

    private int auctionId;
    private String auctionCode;
    private int itemId;
    private String itemName;
    private String itemDescription;
    private String itemCategory;
    private String sellerName;
    private int sellerId;
    private BigDecimal startingPrice;
    private BigDecimal currentPrice;
    private BigDecimal reservePrice;
    private int highestBidderId;
    private String highestBidderUsername;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AuctionStatus status;
    private int extensionCount;
    private int totalBids;

    // thumbnails: nhỏ, nhẹ — dùng cho Dashboard card + thumbnail strip AuctionRoom
    private List<String> thumbnailUrls = new ArrayList<>();
    // fullImages: lớn hơn — dùng cho ảnh main AuctionRoom khi click thumbnail
    private List<String> fullImageUrls = new ArrayList<>();

    public AuctionDTO() {}

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }
    public int getItemId() { return itemId; }
    public void setItemId(int itemId) { this.itemId = itemId; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getItemDescription() { return itemDescription; }
    public void setItemDescription(String itemDescription) { this.itemDescription = itemDescription; }
    public String getItemCategory() { return itemCategory; }
    public void setItemCategory(String itemCategory) { this.itemCategory = itemCategory; }
    public String getSellerName() { return sellerName; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }
    public BigDecimal getStartingPrice() { return startingPrice; }
    public void setStartingPrice(BigDecimal startingPrice) { this.startingPrice = startingPrice; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
    public BigDecimal getReservePrice() { return reservePrice; }
    public void setReservePrice(BigDecimal reservePrice) { this.reservePrice = reservePrice; }
    public int getHighestBidderId() { return highestBidderId; }
    public void setHighestBidderId(int highestBidderId) { this.highestBidderId = highestBidderId; }
    public String getHighestBidderUsername() { return highestBidderUsername; }
    public void setHighestBidderUsername(String u) { this.highestBidderUsername = u; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public AuctionStatus getStatus() { return status; }
    public void setStatus(AuctionStatus status) { this.status = status; }
    public int getExtensionCount() { return extensionCount; }
    public void setExtensionCount(int extensionCount) { this.extensionCount = extensionCount; }
    public int getTotalBids() { return totalBids; }
    public void setTotalBids(int totalBids) { this.totalBids = totalBids; }

    public List<String> getThumbnailUrls() { return thumbnailUrls; }
    public void setThumbnailUrls(List<String> urls) { this.thumbnailUrls = urls != null ? urls : new ArrayList<>(); }

    public List<String> getFullImageUrls() { return fullImageUrls; }
    public void setFullImageUrls(List<String> urls) { this.fullImageUrls = urls != null ? urls : new ArrayList<>(); }

    /** Ảnh đại diện thumbnail — dùng cho Dashboard card. */
    public String getThumbnailUrl() {
        return (thumbnailUrls != null && !thumbnailUrls.isEmpty()) ? thumbnailUrls.get(0) : null;
    }
    /** Backward-compat */
    public String getImageUrl() { return getThumbnailUrl(); }
    public List<String> getImageUrls() { return thumbnailUrls; }
    public void setImageUrls(List<String> urls) { this.thumbnailUrls = urls != null ? urls : new ArrayList<>(); }

    public String getAuctionCode() { return auctionCode; }
    public void setAuctionCode(String auctionCode) { this.auctionCode = auctionCode; }
    public int getSellerId() { return sellerId; }
    public void setSellerId(int sellerId) { this.sellerId = sellerId; }
}