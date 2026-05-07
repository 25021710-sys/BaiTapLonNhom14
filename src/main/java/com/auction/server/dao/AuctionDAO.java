package com.auction.server.dao;

import com.auction.server.config.DatabaseConnection;
import com.auction.server.model.Auction;
import com.auction.server.model.AuctionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AuctionDAO {
    private static final Logger logger = LoggerFactory.getLogger(AuctionDAO.class);

    private Connection getConn() throws SQLException {
        return DatabaseConnection.getConnection();
    }

    public void saveAuction(Auction auction) {
        String sql = """
        
                INSERT INTO auctions (item_id, seller_id, highest_bidder_id, start_time, end_time,
            starting_price, current_price, reserve_price, current_highest_bid, status,
            extension_count, created_at)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
        """;
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, auction.getItemId());
            ps.setInt(2, auction.getSellerId());
            ps.setInt(3, auction.getHighestBidderId());
            ps.setTimestamp(4, Timestamp.valueOf(auction.getStartTime()));
            ps.setTimestamp(5, Timestamp.valueOf(auction.getEndTime()));
            ps.setBigDecimal(6, auction.getStartingPrice());
            ps.setBigDecimal(7, auction.getCurrentPrice());
            ps.setBigDecimal(8, auction.getReservePrice());
            ps.setDouble(9, auction.getCurrentHighestBid());
            ps.setString(10, auction.getStatus().name());
            ps.setInt(11, auction.getExtensionCount());
            ps.setTimestamp(12, Timestamp.valueOf(auction.getCreatedAt()));

            ps.executeUpdate();
            logger.info("Đã lưu auction cho item_id: {}", auction.getItemId());
        } catch (SQLException e) {
            logger.error("Lỗi lưu auction", e);
            throw new RuntimeException(e);
        }
    }

    public Auction findById(int id) {
        String sql = "SELECT * FROM auctions WHERE id = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Auction auction = new Auction();
                    auction.setId(rs.getInt("id"));
                    auction.setItemId(rs.getInt("item_id"));
                    auction.setSellerId(rs.getInt("seller_id"));
                    auction.setHighestBidderId(rs.getInt("highest_bidder_id"));

                    // Xử lý ngày tháng
                    if (rs.getTimestamp("start_time") != null)
                        auction.setStartTime(rs.getTimestamp("start_time").toLocalDateTime());
                    if (rs.getTimestamp("end_time") != null)
                        auction.setEndTime(rs.getTimestamp("end_time").toLocalDateTime());
                    if (rs.getTimestamp("created_at") != null)
                        auction.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());

                    // Xử lý tiền
                    auction.setStartingPrice(rs.getBigDecimal("starting_price"));
                    auction.setCurrentPrice(rs.getBigDecimal("current_price"));
                    auction.setReservePrice(rs.getBigDecimal("reserve_price"));
                    // currentHighestBid vẫn là double
                    auction.setCurrentHighestBid(rs.getDouble("current_highest_bid"));

                    auction.setExtensionCount(rs.getInt("extension_count"));
                    return auction;
                }
            }
        } catch (Exception e) {
            logger.error("Lỗi tìm auction theo id: " + id, e);
        }
        return null;
    }
    /** Cập nhật trạng thái phiên đấu giá */
    public void updateStatus(int auctionId, AuctionStatus status) {
        String sql = "UPDATE auctions SET status = ? WHERE id = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setInt(2, auctionId);
            ps.executeUpdate();
            logger.info("Auction {} -> status {}", auctionId, status);
        } catch (SQLException e) {
            logger.error("Lỗi updateStatus", e);
            throw new RuntimeException(e);
        }
    }
    /** Gia hạn thời gian kết thúc (anti-sniping) */
    public void extendEndTime(int auctionId, LocalDateTime newEndTime) {
        String sql = "UPDATE auctions SET end_time = ?, extension_count = extension_count + 1 WHERE id = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(newEndTime));
            ps.setInt(2, auctionId);
            ps.executeUpdate();
            logger.info("Auction {} gia hạn đến {}", auctionId, newEndTime);
        } catch (SQLException e) {
            logger.error("Lỗi extendEndTime", e);
            throw new RuntimeException(e);
        }
    }
    /**
     * Cập nhật giá hiện tại + người dẫn đầu sau mỗi bid hợp lệ.
     * Dùng optimistic-style UPDATE: chỉ cập nhật nếu current_price vẫn <= newPrice.
     * Trả về true nếu update thành công (tức bid này thắng race).
     */
    public boolean updateBidPrice(int auctionId, int highestBidderId, BigDecimal newPrice) {
        String sql = """
            UPDATE auctions
            SET current_price = ?, highest_bidder_id = ?
            WHERE id = ? AND current_price < ?
            """;
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBigDecimal(1, newPrice);
            ps.setInt(2, highestBidderId);
            ps.setInt(3, auctionId);
            ps.setBigDecimal(4, newPrice);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Lỗi updateBidPrice auctionId={}", auctionId, e);
            throw new RuntimeException(e);
        }
    }

    public void updateAuction(Auction auction) {
        String sql = "UPDATE auctions SET current_price = ?, highest_bidder_id = ?, current_highest_bid = ?, status = ?, extension_count = ? WHERE id = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setBigDecimal(1, auction.getCurrentPrice());
            ps.setInt(2, auction.getHighestBidderId());
            ps.setDouble(3, auction.getCurrentHighestBid());
            ps.setString(4, auction.getStatus().name());
            ps.setInt(5, auction.getExtensionCount());
            ps.setInt(6, auction.getId());

            ps.executeUpdate();

        } catch (Exception e) {
            logger.error("Lỗi cập nhật auction id: " + auction.getId(), e);
            throw new RuntimeException(e);
        }
    }
    /** Lấy tất cả phiên đang OPEN hoặc RUNNING */
    public List<Auction> findActiveAuctions() {
        String sql = "SELECT * FROM auctions WHERE status IN ('OPEN','RUNNING') ORDER BY end_time ASC";
        List<Auction> list = new ArrayList<>();
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            logger.error("Lỗi findActiveAuctions", e);
        }
        return list;
    }

    /** Lấy tất cả phiên (dùng cho Admin) */
    public List<Auction> findAll() {
        String sql = "SELECT * FROM auctions ORDER BY created_at DESC";
        List<Auction> list = new ArrayList<>();
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            logger.error("Lỗi findAll auctions", e);
        }
        return list;
    }
    private Auction mapRow(ResultSet rs) throws SQLException {
        Auction a = new Auction();
        a.setId(rs.getInt("id"));
        a.setItemId(rs.getInt("item_id"));
        a.setSellerId(rs.getInt("seller_id"));
        a.setHighestBidderId(rs.getInt("highest_bidder_id"));

        Timestamp start = rs.getTimestamp("start_time");
        Timestamp end   = rs.getTimestamp("end_time");
        Timestamp created = rs.getTimestamp("created_at");
        if (start   != null) a.setStartTime(start.toLocalDateTime());
        if (end     != null) a.setEndTime(end.toLocalDateTime());
        if (created != null) a.setCreatedAt(created.toLocalDateTime());

        a.setStartingPrice(rs.getBigDecimal("starting_price"));
        a.setCurrentPrice(rs.getBigDecimal("current_price"));
        a.setReservePrice(rs.getBigDecimal("reserve_price"));
        a.setStatus(AuctionStatus.valueOf(rs.getString("status")));
        a.setExtensionCount(rs.getInt("extension_count"));
        return a;
    }
}
