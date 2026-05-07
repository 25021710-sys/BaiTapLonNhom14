package com.auction.server.dao;

import com.auction.server.config.DatabaseConnection;
import com.auction.server.model.Auction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

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
}
