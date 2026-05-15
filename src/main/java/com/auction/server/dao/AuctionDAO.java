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
        INSERT INTO auctions (
            item_id,
            seller_id,
            highest_bidder_id,
            start_time,
            end_time,
            starting_price,
            current_price,
            reserve_price,
            status,
            extension_count
        )
        VALUES (?,?,?,?,?,?,?,?,?,?)
        """;

        try (
                Connection c = getConn();
                // FIX: thêm RETURN_GENERATED_KEYS để lấy ID được DB tự sinh
                PreparedStatement ps = c.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)
        ) {

            ps.setInt(1, auction.getItemId());
            ps.setInt(2, auction.getSellerId());
            if (auction.getHighestBidderId() == 0) {
                ps.setNull(3, java.sql.Types.INTEGER);
            } else {
                ps.setInt(3, auction.getHighestBidderId());
            }

            ps.setTimestamp(4, Timestamp.valueOf(auction.getStartTime()));
            ps.setTimestamp(5, Timestamp.valueOf(auction.getEndTime()));

            ps.setBigDecimal(6, auction.getStartingPrice());
            ps.setBigDecimal(7, auction.getCurrentPrice());
            ps.setBigDecimal(8, auction.getReservePrice());

            ps.setString(9, auction.getStatus().name());

            ps.setInt(10, auction.getExtensionCount());

            ps.executeUpdate();

            // FIX: lấy ID được sinh và gắn ngược vào object auction
            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    auction.setId(generatedKeys.getInt(1));
                }
            }

            logger.info("Đã lưu auction id={} cho item_id: {}", auction.getId(), auction.getItemId());

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

                    // FIX: setStatus bị thiếu trước đây → auction luôn có status = OPEN (mặc định)
                    String statusStr = rs.getString("status");
                    if (statusStr != null) auction.setStatus(AuctionStatus.valueOf(statusStr));

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
     * Điều kiện WHERE: current_price < newPrice (STRICTLY less than).
     * → Đảm bảo chỉ bid CAO HƠN thực sự mới ghi được — không bao giờ 2 người
     *   cùng giá đều thắng, dù request đến gần nhau trong cùng millisecond.
     * Trả về true nếu update thành công (bid này thắng race).
     */
    public boolean updateBidPrice(int auctionId, int highestBidderId, BigDecimal newPrice) {
        String sql = """
            UPDATE auctions
            SET current_price = ?, highest_bidder_id = ?, status = 'RUNNING'
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
        String sql = """
    UPDATE auctions
    SET current_price = ?,
        highest_bidder_id = ?,
        status = ?,
        extension_count = ?
    WHERE id = ?
    """;
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setBigDecimal(1, auction.getCurrentPrice());
            ps.setInt(2, auction.getHighestBidderId());
            ps.setString(3, auction.getStatus().name());
            ps.setInt(4, auction.getExtensionCount());
            ps.setInt(5, auction.getId());

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
    public void updateBidInfo(int auctionId, BigDecimal newPrice,
                              int highestBidderId, int extensionCount, Timestamp newEndTime) {
        // FIX: bỏ cột current_highest_bid không tồn tại trong schema
        String sql = "UPDATE auctions SET current_price = ?, " +
                "highest_bidder_id = ?, extension_count = ?, end_time = ?, status = 'RUNNING' WHERE id = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBigDecimal(1, newPrice);
            ps.setInt(2, highestBidderId);
            ps.setInt(3, extensionCount);
            ps.setTimestamp(4, newEndTime);
            ps.setInt(5, auctionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating bid info for auction {}", auctionId, e);
            throw new RuntimeException(e);
        }
    }
    private Auction mapRow(ResultSet rs) throws SQLException {
        Auction a = new Auction();
        a.setId(rs.getInt("id"));
        a.setItemId(rs.getInt("item_id"));
        a.setSellerId(rs.getInt("seller_id"));
        int highestBidderId = rs.getInt("highest_bidder_id");
        a.setHighestBidderId(rs.wasNull() ? 0 : highestBidderId);

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
    public List<Auction> findPendingAuctions() {

        String sql = """
        SELECT *
        FROM auctions
        WHERE status = 'PENDING'
        ORDER BY created_at DESC
    """;

        List<Auction> list = new ArrayList<>();

        try (
                Connection c = getConn();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()
        ) {

            while (rs.next()) {
                list.add(mapRow(rs));
            }

        } catch (SQLException e) {

            logger.error("Lỗi findPendingAuctions", e);

        }

        return list;
    }

    /**
     * Lấy danh sách phiên PENDING kèm thông tin item và seller (JOIN 1 query thay vì N+1).
     * Được dùng bởi AuctionController.handleGetPendingAuctions() để gửi cho Admin.
     */
    public List<com.auction.common.dto.AdminAuctionRequestDTO> findPendingWithDetails() {
        String sql = """
            SELECT
                a.id            AS auction_id,
                a.seller_id,
                a.item_id,
                a.starting_price,
                a.reserve_price,
                a.start_time,
                a.end_time,
                a.created_at    AS auction_created_at,
                a.status,
                i.name          AS item_name,
                i.description   AS item_description,
                i.category      AS item_category,
                u.username      AS seller_username
            FROM auctions a
            LEFT JOIN items   i ON i.id = a.item_id
            LEFT JOIN users   u ON u.id = a.seller_id
            WHERE a.status = 'PENDING'
            ORDER BY a.created_at DESC
            """;

        List<com.auction.common.dto.AdminAuctionRequestDTO> list = new ArrayList<>();
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                com.auction.common.dto.AdminAuctionRequestDTO dto =
                        new com.auction.common.dto.AdminAuctionRequestDTO();

                dto.setRequestId(rs.getInt("auction_id"));
                dto.setApprovalStatus("PENDING");
                dto.setSellerId(rs.getInt("seller_id"));
                dto.setSellerUsername(rs.getString("seller_username"));
                dto.setItemId(rs.getInt("item_id"));
                dto.setItemName(rs.getString("item_name"));
                dto.setItemDescription(rs.getString("item_description"));
                dto.setItemCategory(rs.getString("item_category"));
                dto.setStartingPrice(rs.getBigDecimal("starting_price"));
                dto.setReservePrice(rs.getBigDecimal("reserve_price"));

                if (rs.getTimestamp("start_time") != null)
                    dto.setStartTime(rs.getTimestamp("start_time").toLocalDateTime());
                if (rs.getTimestamp("end_time") != null)
                    dto.setEndTime(rs.getTimestamp("end_time").toLocalDateTime());
                if (rs.getTimestamp("auction_created_at") != null)
                    dto.setCreatedAt(rs.getTimestamp("auction_created_at").toLocalDateTime());

                // Ảnh: thử đọc file theo quy ước images/<auctionId>.jpg
                String imagePath = "images/" + dto.getRequestId() + ".jpg";
                if (java.nio.file.Files.exists(java.nio.file.Paths.get(imagePath))) {
                    dto.setImageUrl("file:" + java.nio.file.Paths.get(imagePath).toAbsolutePath());
                } else {
                    dto.setImageUrl("https://picsum.photos/seed/" + dto.getRequestId() + "/300/200");
                }

                list.add(dto);
            }
        } catch (SQLException e) {
            logger.error("Lỗi findPendingWithDetails", e);
        }
        return list;
    }

    /**
     * Lấy tất cả phiên đấu giá của một seller (dùng cho trang "My Auctions").
     */
    public List<Auction> findBySeller(int sellerId) {
        String sql = "SELECT * FROM auctions WHERE seller_id = ? AND status IN ('PENDING','OPEN','RUNNING','FINISHED') ORDER BY created_at DESC";        List<Auction> list = new ArrayList<>();
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, sellerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Lỗi findBySeller sellerId={}", sellerId, e);
        }
        return list;
    }

    /**
     * Lấy danh sách auction mà user đã từng đặt giá (dùng cho màn "Phiên tham gia").
     * JOIN với bid_transactions để xác định user có bid hay không.
     */
    public List<Auction> findJoinedByBidder(int bidderId) {
        String sql = """
            SELECT DISTINCT a.*
            FROM auctions a
            INNER JOIN bid_transactions bt ON bt.auction_id = a.id
            WHERE bt.bidder_id = ?
            ORDER BY a.created_at DESC
            """;
        List<Auction> list = new ArrayList<>();
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, bidderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Lỗi findJoinedByBidder bidderId={}", bidderId, e);
        }
        return list;
    }

    public List<Auction> findByStatusExcludeSeller(String status, int excludeSellerId) {
        String sql = "SELECT * FROM auctions WHERE status = ? AND seller_id != ? " +
                "ORDER BY created_at DESC";
        List<Auction> list = new ArrayList<>();
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, excludeSellerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Lỗi findByStatusExcludeSeller", e);
        }
        return list;
    }
}