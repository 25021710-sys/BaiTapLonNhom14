package com.auction.server.dao;
import com.auction.server.config.DatabaseConnection;
import com.auction.server.model.AutoBidConfig;
import com.auction.server.model.BidTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

public class BidDAO {
    private static final Logger log=LoggerFactory.getLogger(BidDAO.class);
    private Connection getConn() throws SQLException{
        return DatabaseConnection.getConnection();
    }
    public void saveBid(BidTransaction bid){
        // language=SQL
        String sql= """
                INSERT INTO bid_transactions
                    (auction_id, bidder_id, amount, is_auto_bid, created_at)
                VALUES(?,?,?,?,?)
                """;
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, bid.getAuctionId());
            ps.setInt(2, bid.getBidderId());
            ps.setBigDecimal(3, bid.getAmount());
            ps.setBoolean(4, bid.isAutoBid());
            ps.setTimestamp(5, Timestamp.valueOf(bid.getCreatedAt()));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Lỗi lưu bid", e);
            throw new RuntimeException(e);
        }
    }
    public void saveAutoBidConfig(AutoBidConfig config) {
        String sql = """
        MERGE INTO auto_bid_configs
            (auction_id, bidder_id, max_bid, increment, created_at)
        KEY(auction_id, bidder_id)
        VALUES (?,?,?,?,?)
        """;
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, config.getAuctionId());
            ps.setInt(2, config.getBidderId());
            ps.setDouble(3, config.getMaxBid().doubleValue());
            ps.setDouble(4, config.getIncrement().doubleValue());
            ps.setTimestamp(5, Timestamp.valueOf(config.getRegisteredAt()));
            ps.executeUpdate();

            log.info("Đã lưu auto-bid config: bidder={}, auction={}",
                    config.getBidderId(), config.getAuctionId());
        } catch (SQLException e) {
            log.error("Lỗi lưu auto-bid config", e);
            throw new RuntimeException(e);
        }
    }

}
