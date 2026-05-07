// ============================================================
// File: CreateItemRequest.java
// ============================================================
package com.auction.common.request;

import java.io.Serializable;
import java.math.BigDecimal;

public class CreateItemRequest implements Serializable {
    private int    itemId;      // 0 khi tạo mới, > 0 khi update
    private String name;
    private String description;
    private BigDecimal startingPrice;
    private int    sellerId;
    private String category;   // "ELECTRONICS" | "ART" | "VEHICLE"

    public CreateItemRequest() {}

    public CreateItemRequest(String name, String description, BigDecimal startingPrice,
                             int sellerId, String category) {
        this.name          = name;
        this.description   = description;
        this.startingPrice = startingPrice;
        this.sellerId      = sellerId;
        this.category      = category;
    }

    public int        getItemId()       { return itemId; }
    public String     getName()         { return name; }
    public String     getDescription()  { return description; }
    public BigDecimal getStartingPrice(){ return startingPrice; }
    public int        getSellerId()     { return sellerId; }
    public String     getCategory()     { return category; }

    public void setItemId(int v)            { this.itemId = v; }
    public void setName(String v)           { this.name = v; }
    public void setDescription(String v)    { this.description = v; }
    public void setStartingPrice(BigDecimal v){ this.startingPrice = v; }
    public void setSellerId(int v)          { this.sellerId = v; }
    public void setCategory(String v)       { this.category = v; }
}