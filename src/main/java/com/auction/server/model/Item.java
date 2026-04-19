package com.auction.server.model;

public abstract class Item extends Entity {
    protected String name;
    protected String description;
    protected double startingPrice;
    protected int sellerId;
    protected String category;

    public Item() { super(); }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }
    public int getSellerId() { return sellerId; }
    public void setSellerId(int sellerId) { this.sellerId = sellerId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}