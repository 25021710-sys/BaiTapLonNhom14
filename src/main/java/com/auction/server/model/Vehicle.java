package com.auction.server.model;

public class Vehicle extends Item {
    private String make;         // Hãng xe (Toyota, Ford)
    private String vehicleModel; // Dòng xe (Camry, Ranger)
    private int year;            // Năm sản xuất
    private int mileage;         // Số km đã đi

    public Vehicle() {
        super();
    }

    public Vehicle(String name, String description, double startingPrice,String sellerId,String make,String vehicleModel,int year,int mileage) {
        super(name, description, startingPrice,sellerId,ItemCategory.VEHICLE);
        this.make=make;
        this.vehicleModel=vehicleModel;
        this.year=year;
        this.mileage=mileage;
    }

    public String getMake() { return make; }
    public void setMake(String make) { this.make = make; }

    public String getVehicleModel() { return vehicleModel; }
    public void setVehicleModel(String vehicleModel) { this.vehicleModel = vehicleModel; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public int getMileage() { return mileage; }
    public void setMileage(int mileage) { this.mileage = mileage; }

    @Override
    public void printInfo() {
        System.out.println("[VEHICLE] " + make + " " + vehicleModel + " (" + year + ") | Đã đi: " + mileage + "km | Giá KĐ: " + startingPrice);
    }
}