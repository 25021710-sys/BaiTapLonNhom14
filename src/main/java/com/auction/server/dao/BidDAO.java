package com.auction.server.dao;

import com.auction.server.config.DatabaseConnection;
import com.auction.server.model.BidTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class BidDAO {
    private static final Logger log = LoggerFactory.getLogger(BidDAO.class);

    private Connection getConn() throws SQLException {
        return DatabaseConnection.getConnection();
    }

    // ── BID TRANSACTION ───────────────────────────────────────────────────────

    public void saveBid(BidTransaction bid) {
        String sql = """
            INSERT INTO bid_transactions (auction_id, bidder_id, amount, is_auto_bid, created_at)
            VALUES (?,?,?,?,?)
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

    /** Lấy lịch sử đặt giá của một phiên, sắp xếp theo thời gian */
    public List<BidTransaction> findByAuction(int auctionId) {
        String sql = "SELECT * FROM bid_transactions WHERE auction_id = ? ORDER BY created_at ASC";
        List<BidTransaction> list = new ArrayList<>();
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapBid(rs));
            }
        } catch (SQLException e) {
            log.error("Lỗi findByAuction auctionId={}", auctionId, e);
        }
        return list;
    }

    /** Đếm tổng số bid của một phiên */
    public int countByAuction(int auctionId) {
        String sql = "SELECT COUNT(*) FROM bid_transactions WHERE auction_id = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            log.error("Lỗi countByAuction auctionId={}", auctionId, e);
        }
        return 0;
    }

    /** Lấy N bid gần nhất của một phiên (dùng cho biểu đồ realtime) */
    public List<BidTransaction> findLatestByAuction(int auctionId, int limit) {
        String sql = """
            SELECT * FROM bid_transactions
            WHERE auction_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;
        List<BidTransaction> list = new ArrayList<>();
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapBid(rs));
            }
        } catch (SQLException e) {
            log.error("Lỗi findLatestByAuction", e);
        }
        return list;
    }

    private BidTransaction mapBid(ResultSet rs) throws SQLException {
        BidTransaction b = new BidTransaction();
        b.setId(rs.getInt("id"));
        b.setAuctionId(rs.getInt("auction_id"));
        b.setBidderId(rs.getInt("bidder_id"));
        b.setAmount(rs.getBigDecimal("amount"));
        //b.setAutoBid(rs.getBoolean("is_auto_bid"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) b.setCreatedAt(ts.toLocalDateTime());
        return b;
    }
}
