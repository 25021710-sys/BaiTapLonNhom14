package com.auction.server.dao;
import com.auction.server.config.DatabaseConnection;
import com.auction.server.model.AutoBidConfig;
import com.auction.server.model.BidTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

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
    /**
     * Hủy cấu hình auto-bid của user cho một phiên đấu giá.
     * * @param bidderId ID của người dùng (kiểu int theo DB)
     * @param auctionId ID của phiên đấu giá (kiểu int theo DB)
     * @return true nếu hủy thành công, false nếu không tìm thấy cấu hình hoặc có lỗi
     */
    public boolean deactivateAutoBid(int bidderId, int auctionId) {
        String sql = "UPDATE auto_bid_configs SET active = FALSE WHERE bidder_id = ? AND auction_id = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, bidderId);
            ps.setInt(2, auctionId);
            // executeUpdate() trả về số dòng bị ảnh hưởng
            int rowsAffected = ps.executeUpdate();
            // Nếu > 0 nghĩa là đã update thành công ít nhất 1 dòng
            return rowsAffected > 0;
        } catch (SQLException e) {
            log.error("Lỗi hủy auto-bid cho bidderId: {} tại auctionId: {}", bidderId, auctionId, e);
            return false;
        }
    }
    public List<AutoBidConfig> findActiveConfigsByAuction(int auctionId) {
        String sql = "SELECT * FROM auto_bid_configs\n" +
                "WHERE auction_id = ? AND active = TRUE\n" +
                "ORDER BY max_bid DESC, registered_at ASC";

        List<AutoBidConfig> list = new ArrayList<>();

        try (
                Connection c = this.getConn();
                PreparedStatement ps = c.prepareStatement(sql)
        ) {
            // Sửa thành setInt để khớp với kiểu int của cột auction_id trong DB
            ps.setInt(1, auctionId);

            try (ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    list.add(this.mapAutoBid(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Lỗi lấy auto-bid configs cho auctionId: {}", auctionId, e);
        }

        return list;
    }
    private AutoBidConfig mapAutoBid(ResultSet rs) throws SQLException {
        AutoBidConfig cfg = new AutoBidConfig();

        cfg.setBidderId(rs.getInt("bidder_id"));
        cfg.setBidderUsername(rs.getString("bidder_username"));

        cfg.setAuctionId(rs.getInt("auction_id"));

        cfg.setMaxBid(rs.getBigDecimal("max_bid"));
        cfg.setIncrement(rs.getBigDecimal("increment"));

        if (rs.getTimestamp("registered_at") != null) {
            cfg.setRegisteredAt(rs.getTimestamp("registered_at").toLocalDateTime());
        }

        // JDBC tự động chuyển tinyint(1) thành boolean
        cfg.setActive(rs.getBoolean("active"));

        return cfg;
    }
}
