package com.auction.server.model;

public enum AuctionStatus {
    PENDING("Chờ duyệt"),
    OPEN("Mở"),
    UPCOMING("Sắp diễn ra"),
    RUNNING("Đang diễn ra"),
    FINISHED("Kết thúc"),
    PAID("Đã thanh toán"),
    CANCELED("Đã hủy");

    private final String display;

    AuctionStatus(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return this.display;
    }

    @Override
    public String toString() {
        return this.display;
    }
}