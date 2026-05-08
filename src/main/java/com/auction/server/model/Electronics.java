package com.auction.server.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Electronics extends Item {
    public Electronics() {
        super();
    }

    public Electronics(int id, String name, String description, BigDecimal startingPrice, int sellerId, ItemCategory category, LocalDateTime createdAt){
        super(id, name, description, startingPrice, sellerId, category, createdAt);
    }

    @Override
    public void printInfo() {
        System.out.println(
                String.format(
                        "[ELECTRONICS] %s | Giá khởi điểm: %s | Mô tả: %s | Thời điểm tạo %s",
                        name,
                        startingPrice,
                        description,
                        createdAt
                )
        );
    }
}