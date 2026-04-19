package com.auction.server.model;

public class Seller extends User {
    private String shopName;

    public Seller() { super(); this.role = "SELLER"; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    @Override
    public void printInfo() { System.out.println("Seller Shop: " + shopName); }
}

