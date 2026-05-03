package com.auction.server.exception;

public class UnauthorizedException extends AuctionException {
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message);
    }
}