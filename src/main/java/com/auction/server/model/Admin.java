package com.auction.server.model;

 public class Admin extends User {
     public Admin() { super(); this.role = "ADMIN"; }

     @Override
     public void printInfo() { System.out.println("Admin: " + username); }
}