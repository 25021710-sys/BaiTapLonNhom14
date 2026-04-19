package com.auction.server.model;

public class Bidder extends User {
    private double balance;

    public Bidder() { super(); this.role = "BIDDER"; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }

    public void deposit(double amount) { this.balance += amount; }
    public void withdraw(double amount) { this.balance -= amount; }

    @Override
    public void printInfo() {
        System.out.println("Bidder: " + username + " | Balance: " + balance);
    }
}