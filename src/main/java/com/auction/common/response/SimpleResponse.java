package com.auction.common.response;

import java.io.Serializable;

public class SimpleResponse implements Serializable {
    private boolean success;
    private String  message;

    public SimpleResponse() {}

    public SimpleResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess()  { return success; }
    public String  getMessage() { return message; }
    public void setSuccess(boolean v) { this.success = v; }
    public void setMessage(String v)  { this.message = v; }
}