package com.auction.server.model;

public enum AuctionStatus {
    OPEN("Mở"),
    RUNNING("Đang diễn ra"),
    FINISHED("Kết thúc"),
    PAID("Đã thanh toán"),
    CANCELED("Đã hủy");

    private final String display;

    private AuctionStatus(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return this.display;
    }

    public String toString() {
        return this.display;
    }
}
