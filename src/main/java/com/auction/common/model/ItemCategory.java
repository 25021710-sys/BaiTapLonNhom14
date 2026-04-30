package com.auction.common.model;

public enum ItemCategory {
    ELECTRONICS("Điện tử"),
    ART("Nghệ thuật"),
    VEHICLE("Phương tiện"),
    OTHER("Khác");

    private final String display;

    private ItemCategory(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return this.display;
    }

    public String toString() {
        return this.display;
    }
}

