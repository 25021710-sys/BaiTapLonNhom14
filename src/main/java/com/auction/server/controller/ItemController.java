package com.auction.server.controller;

import com.auction.common.request.CreateAuctionRequest;
import com.auction.common.response.ItemListResponse;
import com.auction.common.response.SimpleResponse;
import com.auction.server.dao.ItemDAO;
import com.auction.server.model.*;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

public class ItemController {
    private final ItemDAO itemDAO = new ItemDAO();

    public void processRequest(String action, ObjectInputStream in, ObjectOutputStream out) throws Exception {
        switch (action) {

            // ── Lấy tất cả item ───────────────────────────────────────────────
            case "ITEM_GET_ALL" -> {
                List<Item> items = itemDAO.getAllItems();
                out.writeObject(new ItemListResponse(true, "OK", items));
                out.flush();
            }

            // ── Tạo item mới (Seller) ─────────────────────────────────────────
            // phần này sẽ được tạo ở AuctionController nha anh em

            // ── Cập nhật item ─────────────────────────────────────────────────
            // update item để sau nếu có đủ tgian nhé

            // ── Xóa item ──────────────────────────────────────────────────────
            case "ITEM_DELETE" -> {
                int itemId = in.readInt();
                boolean ok = itemDAO.deleteItem(itemId);
                out.writeObject(new SimpleResponse(ok, ok ? "Xóa thành công" : "Xóa thất bại"));
                out.flush();
            }

            // ── Lấy item theo seller ──────────────────────────────────────────
            case "ITEM_GET_BY_SELLER" -> {
                int sellerId = in.readInt();
                List<Item> items = itemDAO.getItemsBySeller(sellerId);
                out.writeObject(new ItemListResponse(true, "OK", items));
                out.flush();
            }
        }
    }
}