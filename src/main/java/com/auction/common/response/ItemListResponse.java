package com.auction.common.response;

import com.auction.server.model.Item;
import java.io.Serializable;
import java.util.List;

public class ItemListResponse implements Serializable {
    private boolean    success;
    private String     message;
    private List<Item> items;

    public ItemListResponse() {}

    public ItemListResponse(boolean success, String message, List<Item> items) {
        this.success = success;
        this.message = message;
        this.items   = items;
    }

    public boolean    isSuccess()  { return success; }
    public String     getMessage() { return message; }
    public List<Item> getItems()   { return items; }
    public void setSuccess(boolean v)      { this.success = v; }
    public void setMessage(String v)       { this.message = v; }
    public void setItems(List<Item> v)     { this.items = v; }
}