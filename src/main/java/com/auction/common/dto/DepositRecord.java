package com.auction.common.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Đại diện 1 dòng lịch sử giao dịch — dùng trong TableView ở BalanceView.
 *
 * type có thể là:
 *   DEPOSIT        – nạp tiền thủ công
 *   WITHDRAW       – rút tiền thủ công
 *   AUCTION_BID    – đặt giá (bị trừ tiền, đang giữ)
 *   AUCTION_REFUND – được hoàn tiền khi bị outbid hoặc phiên hủy/không đạt giá sàn
 *   AUCTION_SALE   – seller nhận tiền khi phiên kết thúc thắng lợi
 */
public class DepositRecord implements Serializable {
    private static final long serialVersionUID = 2L;

    private String type;
    private BigDecimal amount;
    private LocalDateTime createdAt;

    public DepositRecord(String type, BigDecimal amount, LocalDateTime createdAt) {
        this.type      = type;
        this.amount    = amount;
        this.createdAt = createdAt;
    }

    public String getType()             { return type; }
    public BigDecimal getAmount()       { return amount; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    /**
     * Nhãn hiển thị thân thiện cho TableView.
     */
    public String getDisplayType() {
        return switch (type) {
            case "DEPOSIT"        -> "Nạp tiền";
            case "WITHDRAW"       -> "Rút tiền";
            case "AUCTION_BID"    -> "Đặt giá đấu";
            case "AUCTION_REFUND" -> "Hoàn tiền đấu giá";
            case "AUCTION_SALE"   -> "Nhận tiền bán đấu giá";
            default               -> type;
        };
    }

    /**
     * true  → tiền vào (DEPOSIT, AUCTION_REFUND, AUCTION_SALE)
     * false → tiền ra (WITHDRAW, AUCTION_BID)
     */
    public boolean isCredit() {
        return switch (type) {
            case "DEPOSIT", "AUCTION_REFUND", "AUCTION_SALE" -> true;
            default -> false;
        };
    }
}