package com.auction.server.dao;

import com.auction.server.config.DatabaseConnection;
import com.auction.common.model.Auction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

public class AuctionDAO {
    private static final Logger logger= LoggerFactory.getLogger(AuctionDAO.class);
    private Connection getConn() throws SQLException{
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

}
