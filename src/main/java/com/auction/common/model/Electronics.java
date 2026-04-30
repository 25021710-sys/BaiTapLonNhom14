package com.auction.common.model;

public class Electronics extends Item {
    private String brand;
    private String model;
    private int warrantyMonths;

    public Electronics() {
        super();
    }

    public Electronics(String name, String description, double startingPrice,String sellerId,String brand,String model,int warrantyMonths ) {
        super(name,description, startingPrice,sellerId,ItemCategory.ELECTRONICS);
        this.brand=brand;
        this.model=model;
        this.warrantyMonths=warrantyMonths;
    }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getWarrantyMonths() { return warrantyMonths; }
    public void setWarrantyMonths(int warrantyMonths) { this.warrantyMonths = warrantyMonths; }

    @Override
    public void printInfo() {
        System.out.println("[ELECTRONICS] " + brand + " " + model + " | Bảo hành: " + warrantyMonths + " tháng | Giá KĐ: " + startingPrice);
    }
}