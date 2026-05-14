package com.auction.server.dao;

import com.auction.server.config.DatabaseConnection;
import com.auction.server.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

import java.util.*;

public class ItemDAO {
    private static final Logger logger = LoggerFactory.getLogger(ItemDAO.class);
    private Connection getConn() throws SQLException {
        return DatabaseConnection.getConnection();
    }

    // Nhận vào một item và lưu xuống MySQL
    public boolean insertItem(Item item) {
        String sql = "INSERT INTO items (name, description, starting_price, seller_id, category) VALUES (?, ?, ?, ?, ?)";

        // Kết nối Database và chuẩn bị lệnh
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, item.getName());
            pstmt.setString(2, item.getDescription());
            pstmt.setBigDecimal(3, item.getStartingPrice());
            pstmt.setInt(4, item.getSellerId());
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
                itemList.add(mapRow(rs));
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
            pstmt.setBigDecimal(3, item.getStartingPrice());
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
    public List<Item> getItemsBySeller(int sellerId) {
        String sql = "SELECT * FROM items WHERE seller_id = ? ORDER BY created_at DESC";
        List<Item> list = new ArrayList<>();
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, sellerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Lỗi getItemsBySeller sellerId={}", sellerId, e);
        }
        return list;
    }
    private Item mapRow(ResultSet rs) throws SQLException {
        String categoryStr = rs.getString("category");
        ItemCategory category = null;
        try { if (categoryStr != null) category = ItemCategory.valueOf(categoryStr); }
        catch (IllegalArgumentException ignored) {}
        if (category == null) {
            throw new IllegalArgumentException(
                    "Category cannot be null"
            );
        }
        Item item = createItemByCategory(category);

        item.setId(rs.getInt("id"));
        item.setName(rs.getString("name"));
        item.setDescription(rs.getString("description"));
        item.setStartingPrice(rs.getBigDecimal("starting_price"));
        item.setSellerId(rs.getInt("seller_id"));
        item.setCategory(category);

        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) item.setCreatedAt(created.toLocalDateTime());
        return item;
    }
    private Item createItemByCategory(ItemCategory category) {
        return switch (category) {
            case ART -> new Art();
            case ELECTRONICS -> new Electronics();
            case VEHICLE -> new Vehicle();
            default -> throw new IllegalArgumentException(
                    "Unknown category: " + category
            );
        };
    }
    public Item findById(int itemId) {
        String sql = "SELECT * FROM items WHERE id = ?";

        try (
                Connection conn = getConn();
                PreparedStatement ps =
                        conn.prepareStatement(sql)
        ) {
            ps.setInt(1, itemId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            logger.error(
                    "Lỗi findById itemId={}",
                    itemId,
                    e
            );
        }
        return null;
    }
    public Map<Integer, Item> findByIds(Set<Integer> ids) throws SQLException {
        Map<Integer, Item> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) return result;

        StringJoiner placeholders = new StringJoiner(",");
        for (int ignored : ids) placeholders.add("?");

        String sql = "SELECT * FROM items WHERE id IN (" + placeholders + ")";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (int id : ids) ps.setInt(i++, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Item item = mapRow(rs);
                    result.put(item.getId(), item);
                }
            }
        } catch (SQLException e) {
            logger.error("Lỗi findByIds ids={}", ids, e);
            throw e;
        }
        return result;
    }

    public int countBySeller(int sellerId) {
        String sql = "SELECT COUNT(*) FROM items WHERE seller_id = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, sellerId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            logger.error("Loi countBySeller", e);
        }
        return 0;
    }
}