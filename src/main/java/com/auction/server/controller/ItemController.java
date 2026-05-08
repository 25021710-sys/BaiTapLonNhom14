package com.auction.server.controller;

import com.auction.common.request.CreateItemRequest;
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
            case "ITEM_CREATE" -> {
                CreateItemRequest req = (CreateItemRequest) in.readObject();
                try {
                    Item item = createItemFromRequest(req);
                    boolean ok = itemDAO.insertItem(item);
                    out.writeObject(new SimpleResponse(ok,
                            ok ? "Tạo sản phẩm thành công! ID=" + item.getId() : "Lỗi tạo sản phẩm"));
                } catch (Exception e) {
                    out.writeObject(new SimpleResponse(false, "Lỗi: " + e.getMessage()));
                }
                out.flush();
            }

            // ── Cập nhật item ─────────────────────────────────────────────────
            case "ITEM_UPDATE" -> {
                CreateItemRequest req = (CreateItemRequest) in.readObject();
                try {
                    Item item = createItemFromRequest(req);
                    item.setId(req.getItemId());
                    boolean ok = itemDAO.updateItem(item);
                    out.writeObject(new SimpleResponse(ok, ok ? "Cập nhật thành công" : "Cập nhật thất bại"));
                } catch (Exception e) {
                    out.writeObject(new SimpleResponse(false, "Lỗi: " + e.getMessage()));
                }
                out.flush();
            }

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

    /** Factory Method: tạo đúng loại Item dựa vào category */
    private Item createItemFromRequest(CreateItemRequest req) {
        Item item = switch (req.getCategory()) {
            case "ELECTRONICS" -> new Electronics();
            case "ART"         -> new Art();
            case "VEHICLE"     -> new Vehicle();
            default            -> throw new IllegalArgumentException("Danh mục không hợp lệ: " + req.getCategory());
        };
        item.setName(req.getName());
        item.setDescription(req.getDescription());
        item.setStartingPrice(req.getStartingPrice());
        item.setSellerId(req.getSellerId());
        item.setCategory(ItemCategory.valueOf(req.getCategory()));
        return item;
    }
}