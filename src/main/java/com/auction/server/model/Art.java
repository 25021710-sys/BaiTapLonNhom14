package com.auction.server.model;

public class Art extends Item {
    private String artist;
    private int yearCreated;
    private String medium; // chất liệu

    public Art() {
        super();
        this.category = "ART"; // Tự động gán danh mục khi tạo mới
    }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public int getYearCreated() { return yearCreated; }
    public void setYearCreated(int yearCreated) { this.yearCreated = yearCreated; }

    public String getMedium() { return medium; }
    public void setMedium(String medium) { this.medium = medium; }

    @Override
    public void printInfo() {
        System.out.println("[ART] " + name + " | Tác giả: " + artist + " | Năm: " + yearCreated + " | Giá khởi điểm: " + startingPrice);
    }
}