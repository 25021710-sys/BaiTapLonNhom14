package com.auction.common.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Đại diện 1 dòng lịch sử nạp/rút tiền — dùng trong TableView ở BalanceView.
 */
public class DepositRecord implements Serializable {
    private String type;           // "DEPOSIT" hoặc "WITHDRAW"
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
}
