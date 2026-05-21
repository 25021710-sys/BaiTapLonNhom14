package com.auction.common.request;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CreateAuctionRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 2L; // tăng version vì thay đổi field

    private String itemName;
    private String itemDescription;
    private String itemCategory;
    private BigDecimal startingPrice;
    private BigDecimal reservePrice;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /**
     * Danh sách ảnh dạng Base64 (thay thế imageBase64 cũ chỉ chứa 1 ảnh).
     * Index 0 = ảnh đại diện (thumbnail), các index sau = ảnh gallery.
     */
    private List<String> imagesBase64 = new ArrayList<>();

    public CreateAuctionRequest() {}

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getItemDescription() { return itemDescription; }
    public void setItemDescription(String itemDescription) { this.itemDescription = itemDescription; }

    public String getItemCategory() { return itemCategory; }
    public void setItemCategory(String itemCategory) { this.itemCategory = itemCategory; }

    public BigDecimal getStartingPrice() { return startingPrice; }
    public void setStartingPrice(BigDecimal startingPrice) { this.startingPrice = startingPrice; }

    public BigDecimal getReservePrice() { return reservePrice; }
    public void setReservePrice(BigDecimal reservePrice) { this.reservePrice = reservePrice; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public List<String> getImagesBase64() { return imagesBase64; }
    public void setImagesBase64(List<String> imagesBase64) {
        this.imagesBase64 = imagesBase64 != null ? imagesBase64 : new ArrayList<>();
    }

    /** Tiện ích: lấy ảnh đại diện (ảnh đầu tiên), null nếu không có ảnh nào. */
    public String getFirstImageBase64() {
        return (imagesBase64 != null && !imagesBase64.isEmpty()) ? imagesBase64.get(0) : null;
    }
}