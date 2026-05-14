package com.auction.common.dto;

import com.auction.server.model.AuctionStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO chuyên dụng cho màn hình Admin Room Monitoring.
 * Chứa đầy đủ thông tin realtime của một phòng đấu giá:
 * danh sách người tham gia, log hoạt động gần nhất, v.v.
 */
public class AdminRoomDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    // ── Thông tin phòng ───────────────────────────────────────────────────────
    private int auctionId;
    private String itemName;
    private String itemCategory;
    private String sellerName;

    // ── Giá & đặt giá ─────────────────────────────────────────────────────────
    private BigDecimal startingPrice;
    private BigDecimal currentPrice;
    private String highestBidderUsername;
    private int totalBids;

    // ── Thời gian ─────────────────────────────────────────────────────────────
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // ── Trạng thái ────────────────────────────────────────────────────────────
    private AuctionStatus status;

    // ── Participants (chỉ trả về khi admin chọn 1 phòng cụ thể) ──────────────
    private int participantCount;
    private List<String> participantUsernames; // null khi lấy danh sách tổng

    // ── Bid log gần nhất (5 bid cuối) ─────────────────────────────────────────
    private List<String> recentBidLogs; // null khi lấy danh sách tổng

    public AdminRoomDTO() {}

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getItemCategory() { return itemCategory; }
    public void setItemCategory(String itemCategory) { this.itemCategory = itemCategory; }

    public String getSellerName() { return sellerName; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }

    public BigDecimal getStartingPrice() { return startingPrice; }
    public void setStartingPrice(BigDecimal startingPrice) { this.startingPrice = startingPrice; }

    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }

    public String getHighestBidderUsername() { return highestBidderUsername; }
    public void setHighestBidderUsername(String highestBidderUsername) { this.highestBidderUsername = highestBidderUsername; }

    public int getTotalBids() { return totalBids; }
    public void setTotalBids(int totalBids) { this.totalBids = totalBids; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public AuctionStatus getStatus() { return status; }
    public void setStatus(AuctionStatus status) { this.status = status; }

    public int getParticipantCount() { return participantCount; }
    public void setParticipantCount(int participantCount) { this.participantCount = participantCount; }

    public List<String> getParticipantUsernames() { return participantUsernames; }
    public void setParticipantUsernames(List<String> participantUsernames) { this.participantUsernames = participantUsernames; }

    public List<String> getRecentBidLogs() { return recentBidLogs; }
    public void setRecentBidLogs(List<String> recentBidLogs) { this.recentBidLogs = recentBidLogs; }

    /**
     * Tính thời gian còn lại (giây). Trả về 0 nếu đã hết giờ.
     */
    public long getSecondsRemaining() {
        if (endTime == null) return 0;
        long remaining = java.time.Duration.between(LocalDateTime.now(), endTime).getSeconds();
        return Math.max(0, remaining);
    }

    /**
     * Format thời gian còn lại dạng MM:SS hoặc HH:MM:SS
     */
    public String getTimeLeftFormatted() {
        long secs = getSecondsRemaining();
        long h = secs / 3600;
        long m = (secs % 3600) / 60;
        long s = secs % 60;
        if (h > 0) return String.format("%02d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
    }
}
