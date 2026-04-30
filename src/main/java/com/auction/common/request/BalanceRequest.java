package com.auction.common.request;

import java.io.Serializable;
import java.math.BigDecimal;

public class BalanceRequest implements Serializable {
    private int userId;
    private BigDecimal amount;
    private String type; // "DEPOSIT" hoặc "WITHDRAW"

    public BalanceRequest(int userId, BigDecimal amount, String type) {
        this.userId = userId;
        this.amount = amount;
        this.type = type;
    }

    public int getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public String getType() { return type; }
}
