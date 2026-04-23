package com.auction.server.model;

public abstract class Item extends Entity {
    protected String name;
    protected String description;
    protected double startingPrice;
    protected String sellerId;
    protected ItemCategory category;

    public Item() {  super(); }

    public Item(String name,double startingPrice,String sellerId, ItemCategory category){
        super();
        this.name=name;
        this.description = description;
        this.startingPrice=startingPrice;
        this.sellerId=sellerId;
        this.category=category;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }
    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    public ItemCategory getCategory() { return category; }
    public void setCategory(ItemCategory category) { this.category = category; }
}