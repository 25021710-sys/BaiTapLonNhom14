package com.auction.server.exception;
public class AuctionException extends RuntimeException {
    private final String errorCode;

    public AuctionException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AuctionException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return this.errorCode;
    }
}
