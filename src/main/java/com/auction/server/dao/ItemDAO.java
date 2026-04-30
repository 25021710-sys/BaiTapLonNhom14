package com.auction.server.dao;

import com.auction.server.config.DatabaseConnection;
import com.auction.common.model.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.List;

public class ItemDAO {

    // Nhận vào một item và lưu xuống MySQL
    public boolean insertItem(Item item) {
        String sql = "INSERT INTO items (name, description, starting_price, seller_id, category) VALUES (?, ?, ?, ?, ?)";

        // Kết nối Database và chuẩn bị lệnh
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, item.getName());
            pstmt.setString(2, item.getDescription());
            pstmt.setDouble(3, item.getStartingPrice());
            pstmt.setString(4, item.getSellerId());
            // Chuyển Enum (ví dụ: ELECTRONICS) thành dạng chữ "ELECTRONICS" để lưu vào DB
            pstmt.setString(5, item.getCategory().name());

            // lệnh cho MySQL thực thi câu lệnh
            int affectedRows = pstmt.executeUpdate();

            // Nếu lưu thành công, MySQL sẽ tự tạo ra ID. Lấy ID đó gắn ngược lại cho item
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        item.setId(generatedKeys.getInt(1));
                    }
                }
                return true; // Lưu thành công
            }

        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Lỗi khi lưu Item vào kho: " + e.getMessage());
        }

        return false; // Lưu thất bại
    }


    // Lấy toàn bộ dánh sách item ra xem
    public List<Item> getAllItems() {
        List<Item> itemList = new ArrayList<>();
        String sql = "SELECT * FROM items";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) { // executeQuery dùng cho lệnh SELECT

            while (rs.next()) {
                // Dựa vào loại hàng để tạo đúng item
                String categoryStr = rs.getString("category");
                Item item = null;
                if ("ELECTRONICS".equals(categoryStr)) {
                    item = new Electronics();
                } else if ("ART".equals(categoryStr)) {
                    item = new Art();
                } else if ("VEHICLE".equals(categoryStr)) {
                    item = new Vehicle();
                } else {
                    continue;
                }

                item.setId(rs.getInt("id"));
                item.setName(rs.getString("name"));
                item.setDescription(rs.getString("description"));
                item.setStartingPrice(rs.getDouble("starting_price"));
                item.setSellerId(rs.getString("seller_id"));
                // Ép kiểu chữ (String) trong DB về lại kiểu Enum ItemCategory trong Java
                if (categoryStr != null) {
                    item.setCategory(ItemCategory.valueOf(categoryStr));
                }

                itemList.add(item);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Lỗi khi lấy danh sách Item: " + e.getMessage());
        }
        return itemList;
    }


    // Sửa thông tin của item
    public boolean updateItem(Item item) {
        // Cập nhật tên, mô tả, giá... dựa vào cái ID của sản phẩm đó
        String sql = "UPDATE items SET name = ?, description = ?, starting_price = ?, category = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, item.getName());
            pstmt.setString(2, item.getDescription());
            pstmt.setDouble(3, item.getStartingPrice());
            pstmt.setString(4, item.getCategory().name());
            pstmt.setInt(5, item.getId());

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0; // Nếu > 0 tức là đã sửa thành công ít nhất 1 dòng

        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Lỗi khi cập nhật Item: " + e.getMessage());
        }
        return false;
    }


    // Xóa item
    public boolean deleteItem(int itemId) {
        String sql = "DELETE FROM items WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, itemId); // Truyền ID cần xóa vào dấu ?

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Lỗi khi xóa Item: " + e.getMessage());
        }
        return false;
    }
}