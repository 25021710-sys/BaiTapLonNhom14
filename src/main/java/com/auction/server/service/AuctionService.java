package com.auction.server.service;

import com.auction.common.dto.AuctionUpdateDTO;
import com.auction.common.request.BidRequest;
import com.auction.common.response.BidResponse;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.BidDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.exception.AuctionException;
import com.auction.server.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AuctionService – lớp nghiệp vụ trung tâm của hệ thống đấu giá.
 *
 * Trách nhiệm:
 *   - Tạo / duyệt / từ chối phiên đấu giá
 *   - Xử lý đặt giá thủ công (placeBid) và tự động (placeAutoBid)
 *   - Quản lý cache phiên và lock per-auction (thread-safe)
 *   - Anti-sniping: gia hạn phiên khi bid sát giờ kết thúc
 *   - Broadcast realtime update qua AuctionManager (Observer pattern)
 *   - Kích hoạt chuỗi auto-bid sau mỗi bid thủ công
 *
 */
public class AuctionService {

    private static final Logger log = LoggerFactory.getLogger(AuctionService.class);

    // ── Hằng số nghiệp vụ ────────────────────────────────────────────────────
    /** Số giây cuối phiên: nếu có bid trong khoảng này sẽ kích hoạt anti-snipe */
    private static final int ANTI_SNIPE_THRESHOLD_SECONDS = 30;
    /** Số giây gia hạn thêm khi anti-snipe kích hoạt */
    private static final int ANTI_SNIPE_EXTEND_SECONDS    = 60;

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final AuctionDAO    auctionDAO;
    private final BidDAO        bidDAO;
    private final AutoBidEngine autoBidEngine;
    private final UserDAO       userDAO = new UserDAO();

    /** Được inject sau khi khởi tạo (tránh circular dependency với AuctionManager) */
    private AuctionManager auctionManager;

    // ── Thread-safety ─────────────────────────────────────────────────────────
    /** Cache phiên đấu giá đang hoạt động: auctionId → Auction */
    private final ConcurrentHashMap<Integer, Auction>       auctionCache  = new ConcurrentHashMap<>();
    /** Lock per-auction (fair=true để tránh starvation): auctionId → Lock */
    private final ConcurrentHashMap<Integer, ReentrantLock> auctionLocks  = new ConcurrentHashMap<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    public AuctionService(AuctionDAO auctionDAO, BidDAO bidDAO, AutoBidEngine autoBidEngine) {
        this.auctionDAO    = auctionDAO;
        this.bidDAO        = bidDAO;
        this.autoBidEngine = autoBidEngine;
    }

    public void setAuctionManager(AuctionManager manager) {
        this.auctionManager = manager;
    }

    /** Expose cache để AuctionManager scheduler có thể cập nhật status khi phiên tự mở. */
    public ConcurrentHashMap<Integer, Auction> getAuctionCache() {
        return auctionCache;
    }

    // ── CREATE AUCTION ────────────────────────────────────────────────────────

    /**
     * Tạo phiên đấu giá mới với trạng thái PENDING (chờ Admin duyệt).
     *
     * @throws AuctionException nếu tham số không hợp lệ
     */
    public Auction createAuction(int itemId, int sellerId,
                                 BigDecimal startingPrice,
                                 BigDecimal reservePrice,
                                 LocalDateTime startTime,
                                 LocalDateTime endTime) {

        // Validate thời gian
        if (startTime == null || endTime == null)
            throw new AuctionException("INVALID_TIME", "Thời gian bắt đầu và kết thúc không được để trống.");
        if (!endTime.isAfter(startTime))
            throw new AuctionException("INVALID_TIME", "Thời gian kết thúc phải sau thời gian bắt đầu.");

        // Validate giá
        if (startingPrice == null || startingPrice.compareTo(BigDecimal.ZERO) <= 0)
            throw new AuctionException("INVALID_PRICE", "Giá khởi điểm phải lớn hơn 0.");

        Auction auction = new Auction(itemId, sellerId, startingPrice, startTime, endTime);
        auction.setReservePrice(reservePrice != null ? reservePrice : startingPrice);
        auction.setCurrentPrice(startingPrice);
        auction.setStatus(AuctionStatus.PENDING);
        auctionDAO.saveAuction(auction);

        log.info("Tạo phiên đấu giá: id={} | itemId={} | sellerId={}", auction.getId(), itemId, sellerId);
        return auction;
    }

    // ── PLACE BID (thủ công) ──────────────────────────────────────────────────

    /**
     * Xử lý đặt giá thủ công từ client.
     *
     * Luồng xử lý (toàn bộ bên trong ReentrantLock):
     *   1. Kiểm tra phiên tồn tại
     *   2. Kiểm tra trạng thái & thời gian
     *   3. Kiểm tra seller không tự bid
     *   4. Kiểm tra giá hợp lệ (> currentPrice và >= minBidIncrement)
     *   5. Kiểm tra không tự outbid chính mình
     *   6. Kiểm tra số dư
     *   7. Hoàn tiền người dẫn đầu cũ
     *   8. Trừ tiền người đặt
     *   9. Cập nhật auction (DB + cache)
     *  10. Lưu BidTransaction
     *  11. Anti-sniping
     *  12. Broadcast realtime update
     *  13. Kích hoạt chuỗi auto-bid
     */
    public BidResponse placeBid(BidRequest request) {
        int        auctionId = Integer.parseInt(request.getAuctionId());
        int        userId    = request.getUserId();
        BigDecimal bidAmount = request.getAmount();

        if (bidAmount == null || bidAmount.compareTo(BigDecimal.ZERO) <= 0)
            return new BidResponse(false, "Giao dịch bị từ chối: Số tiền đặt giá không hợp lệ.", BigDecimal.ZERO);

        ReentrantLock lock = auctionLocks.computeIfAbsent(auctionId, k -> new ReentrantLock(true));
        lock.lock();

        try {
            // 1. Kiểm tra phiên tồn tại — đọc FRESH từ DB bên trong lock
            //    để tránh đọc cache stale khi 2 request vào lock liên tiếp rất nhanh
            Auction auction = auctionCache.get(auctionId);
            if (auction == null) {
                try {
                    auction = auctionDAO.findById(auctionId);
                    if (auction == null)
                        return new BidResponse(false, "Phiên đấu giá không tồn tại.", BigDecimal.ZERO);
                    auctionCache.put(auctionId, auction);
                } catch (Exception e) {
                    return new BidResponse(false, "Phiên đấu giá không tồn tại.", BigDecimal.ZERO);
                }
            }

            BigDecimal currentPrice = auction.getCurrentPrice() != null
                    ? auction.getCurrentPrice()
                    : auction.getStartingPrice();

            // 2. Kiểm tra trạng thái phiên
            AuctionStatus status = auction.getStatus();
            if (status != AuctionStatus.OPEN && status != AuctionStatus.RUNNING) {
                return new BidResponse(false,
                        "Giao dịch bị từ chối: Phiên đấu giá đang ở trạng thái "
                                + status.getDisplay() + ".", currentPrice);
            }

            // 3. Kiểm tra thời gian
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(auction.getStartTime())) {
                return new BidResponse(false,
                        "Giao dịch bị từ chối: Phiên đấu giá chưa bắt đầu.", currentPrice);
            }
            if (now.isAfter(auction.getEndTime())) {
                return new BidResponse(false,
                        "Giao dịch bị từ chối: Phiên đấu giá đã kết thúc.", currentPrice);
            }

            // 4. Seller không được tự bid vào phiên của mình
            if (auction.getSellerId() == userId) {
                return new BidResponse(false,
                        "Giao dịch bị từ chối: Người bán không thể tự đấu giá sản phẩm của mình.",
                        currentPrice);
            }

            // 5. Kiểm tra giá hợp lệ: phải STRICTLY cao hơn currentPrice
            if (bidAmount.compareTo(currentPrice) <= 0) {
                return new BidResponse(false,
                        "Giao dịch bị từ chối: Mức giá đề xuất phải cao hơn giá hiện tại ("
                                + currentPrice + ").", currentPrice);
            }

            // 6. Kiểm tra bước giá tối thiểu — áp dụng LUÔN LUÔN (không chỉ khi đã có bid)
            BigDecimal minIncrement = auction.getMinBidIncrement();
            if (bidAmount.compareTo(currentPrice.add(minIncrement)) < 0) {
                return new BidResponse(false,
                        "Giao dịch bị từ chối: Mức giá tối thiểu phải cao hơn giá hiện tại ít nhất "
                                + minIncrement + " VND.", currentPrice);
            }

            // 6b. Người đang dẫn đầu KHÔNG được phép bid thêm cho đến khi bị người khác vượt giá
            int previousBidderId = auction.getHighestBidderId();
            boolean isSelfRaise  = (previousBidderId != 0 && previousBidderId == userId);

            if (isSelfRaise) {
                return new BidResponse(false,
                        "Giao dịch bị từ chối: Bạn đang dẫn đầu. Chỉ có thể đặt giá tiếp khi bị người khác vượt qua.",
                        currentPrice);
            }

            // 7. Kiểm tra số dư
            //    - Nếu người đang dẫn đầu tự nâng giá: chỉ cần trả phần chênh lệch
            //      vì tiền cũ (currentPrice) đã bị giữ, chỉ cần thêm (bidAmount - currentPrice)
            //    - Nếu là bidder mới: cần trả đủ bidAmount
            User user;
            try {
                user = userDAO.findById(userId);
            } catch (Exception e) {
                log.error("Lỗi truy vấn user {}: {}", userId, e.getMessage());
                return new BidResponse(false, "Lỗi kiểm tra tài khoản, vui lòng thử lại.", currentPrice);
            }
            if (user == null) {
                return new BidResponse(false, "Tài khoản không tồn tại.", currentPrice);
            }

            BigDecimal requiredBalance = bidAmount; // bidder mới cần trả đủ bidAmount

            if (user.getBalance().compareTo(requiredBalance) < 0) {
                return new BidResponse(false,
                        "Giao dịch bị từ chối: Số dư tài khoản không đủ (hiện có: "
                                + user.getBalance() + " VND).",
                        currentPrice);
            }

            // 9. Hoàn tiền cho người dẫn đầu trước đó (nếu có và khác userId)
            if (previousBidderId != 0 && previousBidderId != userId) {
                refundPreviousBidder(previousBidderId, currentPrice);
            }

            // 10. Trừ tiền người đặt giá mới
            try {
                user.setBalance(user.getBalance().subtract(requiredBalance));
                userDAO.updateBalance(user.getId(), user.getBalance());
            } catch (Exception e) {
                log.error("Lỗi trừ tiền user {}: {}", userId, e.getMessage());
                // Rollback: hoàn lại tiền cho previousBidder nếu đã hoàn
                if (previousBidderId != 0 && previousBidderId != userId) {
                    chargeUser(previousBidderId, currentPrice);
                }
                return new BidResponse(false, "Lỗi cập nhật số dư, vui lòng thử lại.", currentPrice);
            }

            // 11. Cập nhật auction — dùng optimistic UPDATE với điều kiện current_price < bidAmount
            //     Điều này đảm bảo chỉ DUY NHẤT một bid thắng race dù 2 request vào lock tuần tự
            //     với cùng bidAmount (vì người thứ 2 sẽ thấy current_price đã bằng bidAmount → 0 rows)
            auction.setCurrentPrice(bidAmount);
            auction.setHighestBidderId(userId);
            auction.setStatus(AuctionStatus.RUNNING);
            boolean dbUpdated = auctionDAO.updateBidPrice(auctionId, userId, bidAmount);
            if (!dbUpdated) {
                // DB đã có giá >= bidAmount (người khác vừa thắng race ở DB level)
                // Rollback: hoàn lại khoản tiền đã trừ ở bước 10
                refundPreviousBidder(userId, requiredBalance);
                if (previousBidderId != 0 && previousBidderId != userId) {
                    // Hoàn ngược lại tiền đã refund cho previousBidder ở bước 9
                    chargeUser(previousBidderId, currentPrice);
                }
                // Làm mới cache từ DB để lần sau đọc đúng
                Auction fresh = auctionDAO.findById(auctionId);
                if (fresh != null) auctionCache.put(auctionId, fresh);
                return new BidResponse(false,
                        "Giao dịch bị từ chối: Đã có người đặt giá cao hơn vừa xong. Vui lòng thử lại.",
                        fresh != null && fresh.getCurrentPrice() != null
                                ? fresh.getCurrentPrice() : currentPrice);
            }
            auctionCache.put(auctionId, auction);

            // 12. Lưu lịch sử giao dịch
            BidTransaction transaction = new BidTransaction(0, now, auctionId, userId, bidAmount, false);
            bidDAO.saveBid(transaction);

            // 13. Anti-sniping
            boolean extended = checkAndExtend(auction);

            // 14. Broadcast realtime update đến tất cả client đang xem phiên
            if (auctionManager != null) {
                AuctionUpdateDTO.UpdateType updateType = extended
                        ? AuctionUpdateDTO.UpdateType.AUCTION_EXTENDED
                        : AuctionUpdateDTO.UpdateType.BID_PLACED;
                String message = extended
                        ? "Phiên được gia hạn do có bid trong " + ANTI_SNIPE_THRESHOLD_SECONDS + " giây cuối!"
                        : "Bid mới từ " + user.getUsername() + "!";

                int participantCount = auctionManager.getParticipantCount(auctionId);
                auctionManager.broadcastUpdate(auctionId, new AuctionUpdateDTO(
                        auctionId, updateType, bidAmount,
                        userId, user.getUsername(),
                        auction.getEndTime(), message, participantCount
                ));
            }

            // 15. Kích hoạt chuỗi auto-bid (nếu có config đang active)
            BidTransaction autoBidTx = autoBidEngine.triggerAutoBid(auctionId, userId, bidAmount);
            if (autoBidTx != null && auctionManager != null) {
                // Lấy username thật của auto-bidder (không dùng chuỗi "auto-bid")
                String autoBidderName = resolveUsername(autoBidTx.getBidderId());
                auctionManager.broadcastUpdate(auctionId, new AuctionUpdateDTO(
                        auctionId,
                        AuctionUpdateDTO.UpdateType.BID_PLACED,
                        autoBidTx.getAmount(),
                        autoBidTx.getBidderId(),
                        autoBidderName + " (auto)",
                        auction.getEndTime(),
                        "Auto-bid từ " + autoBidderName + "!"
                ));
            }

            log.info("Bid thành công: userId={} đặt {} VND cho auctionId={}", userId, bidAmount, auctionId);
            return new BidResponse(true,
                    "Giao dịch thành công. Mức giá mới: " + bidAmount + " VND.", bidAmount);

        } catch (Exception e) {
            log.error("Lỗi nghiêm trọng trong placeBid (auctionId={}, userId={}): {}",
                    auctionId, userId, e.getMessage(), e);
            return new BidResponse(false, "Lỗi server, vui lòng thử lại sau.", BigDecimal.ZERO);
        } finally {
            lock.unlock();
        }
    }

    // ── PLACE AUTO BID (nội bộ, gọi từ AutoBidEngine) ────────────────────────

    /**
     * Đặt auto-bid thay người dùng. Được AutoBidEngine gọi sau khi có bid thủ công.
     *
     * Khác với placeBid:
     *  - KHÔNG gọi triggerAutoBid lại (tránh đệ quy vô hạn)
     *  - Có lock riêng để thread-safe với placeBid đang chạy song song
     *
     * @return BidTransaction nếu thành công, null nếu thất bại (không đủ tiền, phiên đóng, ...)
     */
    public BidTransaction placeAutoBid(int auctionId, int bidderId, BigDecimal amount) {
        ReentrantLock lock = auctionLocks.computeIfAbsent(auctionId, k -> new ReentrantLock(true));
        lock.lock();

        try {
            // 1. Kiểm tra phiên tồn tại
            Auction auction;
            try {
                auction = getAuction(auctionId);
            } catch (AuctionException e) {
                log.warn("Auto-bid thất bại: phiên {} không tồn tại.", auctionId);
                return null;
            }

            // 2. Kiểm tra trạng thái phiên
            AuctionStatus status = auction.getStatus();
            if (status != AuctionStatus.OPEN && status != AuctionStatus.RUNNING) {
                log.warn("Auto-bid thất bại: phiên {} ở trạng thái {}.", auctionId, status);
                return null;
            }

            // 3. Kiểm tra thời gian (phiên có thể vừa đóng lúc auto-bid kích hoạt)
            LocalDateTime now = LocalDateTime.now();
            if (now.isAfter(auction.getEndTime())) {
                log.warn("Auto-bid thất bại: phiên {} đã hết giờ.", auctionId);
                return null;
            }

            // 4. Seller không tự auto-bid auction của mình
            if (auction.getSellerId() == bidderId) {
                log.warn("Auto-bid thất bại: bidderId {} là seller của phiên {}.", bidderId, auctionId);
                return null;
            }

            // 5. Giá auto-bid phải cao hơn giá hiện tại
            BigDecimal currentPrice = auction.getCurrentPrice() != null
                    ? auction.getCurrentPrice()
                    : auction.getStartingPrice();
            if (amount.compareTo(currentPrice) <= 0) {
                log.warn("Auto-bid thất bại: amount={} không cao hơn currentPrice={}.", amount, currentPrice);
                return null;
            }

            // 6. Hoàn tiền cho người dẫn đầu trước đó
            int previousBidderId = auction.getHighestBidderId();
            if (previousBidderId != 0 && previousBidderId != bidderId) {
                refundPreviousBidder(previousBidderId, currentPrice);
            }

            // 7. Kiểm tra và trừ tiền auto-bidder
            User bidder;
            try {
                bidder = userDAO.findById(bidderId);
            } catch (Exception e) {
                log.warn("Auto-bid thất bại: không lấy được user {}: {}", bidderId, e.getMessage());
                // Rollback: hoàn lại tiền cho previousBidder
                if (previousBidderId != 0 && previousBidderId != bidderId) {
                    chargeUser(previousBidderId, currentPrice);
                }
                return null;
            }

            if (bidder == null) {
                log.warn("Auto-bid thất bại: user {} không tồn tại.", bidderId);
                return null;
            }
            if (bidder.getBalance().compareTo(amount) < 0) {
                log.warn("Auto-bid thất bại: user {} không đủ tiền (balance={}, cần={}).",
                        bidderId, bidder.getBalance(), amount);
                // Rollback hoàn tiền
                if (previousBidderId != 0 && previousBidderId != bidderId) {
                    chargeUser(previousBidderId, currentPrice);
                }
                return null;
            }

            try {
                bidder.setBalance(bidder.getBalance().subtract(amount));
                userDAO.updateBalance(bidderId, bidder.getBalance());
            } catch (Exception e) {
                log.warn("Auto-bid thất bại: lỗi trừ tiền user {}: {}", bidderId, e.getMessage());
                if (previousBidderId != 0 && previousBidderId != bidderId) {
                    chargeUser(previousBidderId, currentPrice);
                }
                return null;
            }

            // 8. Lưu BidTransaction
            BidTransaction bid = new BidTransaction(0, now, auctionId, bidderId, amount, true);
            bidDAO.saveBid(bid);

            // 9. Cập nhật auction (DB trước, rồi cache)
            auctionDAO.updateBidPrice(auctionId, bidderId, amount);
            auction.setCurrentPrice(amount);
            auction.setHighestBidderId(bidderId);
            auction.setStatus(AuctionStatus.RUNNING);
            auctionCache.put(auctionId, auction);

            // 10. Anti-sniping
            checkAndExtend(auction);

            log.info("Auto-bid thành công: auctionId={} bidderId={} amount={}", auctionId, bidderId, amount);
            return bid;

        } catch (Exception e) {
            log.error("Lỗi nghiêm trọng trong placeAutoBid (auctionId={}, bidderId={}): {}",
                    auctionId, bidderId, e.getMessage(), e);
            return null;
        } finally {
            lock.unlock();
        }
    }

    // ── CLOSE AUCTION ─────────────────────────────────────────────────────────

    /**
     * Đóng phiên đấu giá khi hết giờ (được gọi từ AuctionManager scheduler).
     *
     * Nếu giá thắng không đạt reservePrice → hoàn tiền người dẫn đầu.
     */
    public void closeAuction(Auction auction) {
        auction.setStatus(AuctionStatus.FINISHED);
        auctionDAO.updateStatus(auction.getId(), AuctionStatus.FINISHED);
        auctionCache.remove(auction.getId());

        BigDecimal currentPrice = auction.getCurrentPrice();
        BigDecimal reservePrice = auction.getReservePrice();
        int winnerId  = auction.getHighestBidderId();
        int sellerId  = auction.getSellerId();

        if (winnerId != 0 && reservePrice != null
                && currentPrice != null
                && currentPrice.compareTo(reservePrice) < 0) {
            // Không đạt giá sàn → hoàn tiền người thắng, không cộng cho seller
            log.info("Phiên {} không đạt giá sàn. Hoàn tiền bidderId={}.",
                    auction.getId(), winnerId);
            refundPreviousBidder(winnerId, currentPrice);
            auction.setHighestBidderId(0);

        } else if (winnerId != 0 && currentPrice != null
                && currentPrice.compareTo(BigDecimal.ZERO) > 0) {
            // ✅ Có người thắng + đạt giá sàn → cộng tiền cho seller
            try {
                User seller = userDAO.findById(sellerId);
                if (seller != null) {
                    seller.setBalance(seller.getBalance().add(currentPrice));
                    userDAO.updateBalance(sellerId, seller.getBalance());
                    log.info("Cộng {} VNĐ cho seller id={} (phiên {})",
                            currentPrice, sellerId, auction.getId());
                }
            } catch (Exception e) {
                log.error("Lỗi cộng tiền seller id={}: {}", sellerId, e.getMessage());
            }
        }

        log.info("Phiên {} kết thúc. Winner={}, giá={}",
                auction.getId(), auction.getHighestBidderId(), currentPrice);
    }

    // ── ADMIN ─────────────────────────────────────────────────────────────────

    /**
     * Admin duyệt phiên đấu giá: PENDING → OPEN.
     * Sau khi duyệt, phiên được load vào cache và auto-bid config được nạp.
     *
     * @return true nếu thành công, false nếu phiên không tồn tại hoặc không đúng trạng thái
     */
    public boolean approveAuction(int auctionId, int adminId) {
        Auction auction = auctionDAO.findById(auctionId);
        if (auction == null) {
            log.warn("approveAuction: phiên {} không tồn tại.", auctionId);
            return false;
        }
        if (auction.getStatus() != AuctionStatus.PENDING) {
            log.warn("approveAuction: phiên {} không ở trạng thái PENDING (hiện: {}).",
                    auctionId, auction.getStatus());
            return false;
        }

        auction.setStatus(AuctionStatus.OPEN);
        auctionDAO.updateStatus(auctionId, AuctionStatus.OPEN);
        auctionCache.put(auctionId, auction);
        auctionLocks.computeIfAbsent(auctionId, k -> new ReentrantLock(true));
        autoBidEngine.loadFromDb(auctionId);

        log.info("Admin {} duyệt phiên {}.", adminId, auctionId);
        return true;
    }

    /**
     * Admin từ chối phiên đấu giá: PENDING → CANCELED.
     *
     * @return true nếu thành công, false nếu phiên không tồn tại hoặc không đúng trạng thái
     */
    public boolean rejectAuction(int auctionId, int adminId, String reason) {
        Auction auction = auctionDAO.findById(auctionId);
        if (auction == null) {
            log.warn("rejectAuction: phiên {} không tồn tại.", auctionId);
            return false;
        }
        if (auction.getStatus() != AuctionStatus.PENDING) {
            log.warn("rejectAuction: phiên {} không ở trạng thái PENDING (hiện: {}).",
                    auctionId, auction.getStatus());
            return false;
        }

        auction.setStatus(AuctionStatus.CANCELED);
        auctionDAO.updateStatus(auctionId, AuctionStatus.CANCELED);

        log.info("Admin {} từ chối phiên {} | Lý do: {}", adminId, auctionId, reason);
        return true;
    }

    // ── QUERIES ───────────────────────────────────────────────────────────────

    /**
     * Lấy phiên đấu giá từ cache; nếu chưa có thì load từ DB.
     *
     * @throws AuctionException nếu không tìm thấy
     */
    public Auction getAuction(int auctionId) {
        return auctionCache.computeIfAbsent(auctionId, id -> {
            Auction a = auctionDAO.findById(id);
            if (a == null)
                throw new AuctionException("NOT_FOUND", "Phiên đấu giá không tồn tại: " + id);
            auctionLocks.computeIfAbsent(id, k -> new ReentrantLock(true));
            return a;
        });
    }

    /** Load tất cả phiên OPEN/RUNNING vào cache khi server khởi động. */
    public void loadActiveAuctions() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        List<Auction> active = auctionDAO.findActiveAuctions();
        int autoOpened = 0;
        for (Auction a : active) {
            // Nếu phiên OPEN đã qua startTime mà chưa được mở → mở luôn khi load
            if (a.getStatus() == AuctionStatus.OPEN
                    && a.getStartTime() != null
                    && !now.isBefore(a.getStartTime())) {
                a.setStatus(AuctionStatus.RUNNING);
                auctionDAO.updateStatus(a.getId(), AuctionStatus.RUNNING);
                autoOpened++;
                log.info("loadActiveAuctions: tự mở phiên {} (startTime={} đã qua)",
                        a.getId(), a.getStartTime());
            }
            auctionCache.put(a.getId(), a);
            auctionLocks.put(a.getId(), new ReentrantLock(true));
            autoBidEngine.loadFromDb(a.getId());
        }
        log.info("Đã load {} phiên vào cache ({} phiên tự mở do qua startTime).",
                active.size(), autoOpened);
    }

    /** Lấy danh sách phiên OPEN/RUNNING (cho Dashboard). */
    public List<Auction> getActiveAuctions() {
        return auctionDAO.findActiveAuctions();
    }

    /** Lấy danh sách phiên PENDING (cho Admin duyệt). */
    public List<Auction> getPendingAuctions() {
        return auctionDAO.findPendingAuctions();
    }

    public List<Auction> getAuctionsBySeller(int sellerId) {
        return auctionDAO.findBySeller(sellerId);
    }

    // ── PRIVATE HELPERS ───────────────────────────────────────────────────────

    /**
     * Anti-sniping: nếu bid xuất hiện trong X giây cuối → gia hạn thêm Y giây.
     *
     * @return true nếu đã gia hạn
     */
    private boolean checkAndExtend(Auction auction) {
        long secondsLeft = Duration.between(LocalDateTime.now(), auction.getEndTime()).getSeconds();
        if (secondsLeft > 0 && secondsLeft < ANTI_SNIPE_THRESHOLD_SECONDS) {
            LocalDateTime newEnd = auction.getEndTime().plusSeconds(ANTI_SNIPE_EXTEND_SECONDS);
            auction.setEndTime(newEnd);
            auction.setExtensionCount(auction.getExtensionCount() + 1);
            auctionDAO.extendEndTime(auction.getId(), newEnd);
            log.info("Anti-snipe: phiên {} gia hạn đến {} (lần {})",
                    auction.getId(), newEnd, auction.getExtensionCount());
            return true;
        }
        return false;
    }

    /**
     * Hoàn tiền cho người dẫn đầu trước khi bị outbid.
     * Log warning nếu thất bại nhưng không throw (không chặn bid đang diễn ra).
     */
    private void refundPreviousBidder(int bidderId, BigDecimal amount) {
        try {
            User prev = userDAO.findById(bidderId);
            if (prev != null) {
                prev.setBalance(prev.getBalance().add(amount));
                userDAO.updateBalance(prev.getId(), prev.getBalance());
                log.debug("Hoàn {} VND cho userId={}", amount, bidderId);
            }
        } catch (Exception e) {
            log.warn("Lỗi hoàn tiền cho userId={}: {}", bidderId, e.getMessage());
        }
    }

    /**
     * Trừ tiền người dùng (dùng khi rollback hoàn tiền thất bại).
     * Log warning nếu thất bại.
     */
    private void chargeUser(int userId, BigDecimal amount) {
        try {
            User user = userDAO.findById(userId);
            if (user != null && user.getBalance().compareTo(amount) >= 0) {
                user.setBalance(user.getBalance().subtract(amount));
                userDAO.updateBalance(userId, user.getBalance());
                log.debug("Rollback: trừ {} VND của userId={}", amount, userId);
            }
        } catch (Exception e) {
            log.warn("Lỗi rollback trừ tiền userId={}: {}", userId, e.getMessage());
        }
    }

    /**
     * Tra cứu username theo userId. Trả về chuỗi rỗng nếu thất bại.
     */
    private String resolveUsername(int userId) {
        if (userId == 0) return "";
        try {
            User u = userDAO.findById(userId);
            return u != null ? u.getUsername() : "";
        } catch (Exception e) {
            log.debug("Không lấy được username userId={}: {}", userId, e.getMessage());
            return "";
        }
    }

    public List<Auction> getJoinedAuctions(int bidderId) {
        return auctionDAO.findJoinedByBidder(bidderId);
    }

    public List<Auction> getRunningAuctionsExcludeSeller(int sellerId) {
        return auctionDAO.findByStatusExcludeSeller("RUNNING", sellerId);
    }

    public List<Auction> getOpenAuctionsExcludeSeller(int sellerId) {
        return auctionDAO.findByStatusExcludeSeller("OPEN", sellerId);
    }
    public boolean pauseAuction(int auctionId, int adminId) {
        ReentrantLock lock = auctionLocks.computeIfAbsent(auctionId, k -> new ReentrantLock(true));
        lock.lock();
        try {
            Auction auction = auctionCache.get(auctionId);
            if (auction == null) auction = auctionDAO.findById(auctionId);
            if (auction == null) return false;
            if (auction.getStatus() != AuctionStatus.RUNNING) return false;

            auction.setStatus(AuctionStatus.PAUSED);  // thay vì OPEN
            boolean ok = auctionDAO.updateStatus(auctionId, AuctionStatus.PAUSED);
            if (ok) {
                auctionCache.put(auctionId, auction);
                log.info("Admin {} tạm dừng phòng {}", adminId, auctionId);
            }
            return ok;
        } finally {
            lock.unlock();
        }
    }
    public boolean resumeAuction(int auctionId, int adminId) {
        ReentrantLock lock = auctionLocks.computeIfAbsent(auctionId, k -> new ReentrantLock(true));
        lock.lock();
        try {
            Auction auction = auctionCache.get(auctionId);
            if (auction == null) auction = auctionDAO.findById(auctionId);
            if (auction == null) return false;
            if (auction.getStatus() != AuctionStatus.PAUSED) return false;  // check đúng

            auction.setStatus(AuctionStatus.RUNNING);
            boolean ok = auctionDAO.updateStatus(auctionId, AuctionStatus.RUNNING);
            if (ok) {
                auctionCache.put(auctionId, auction);
                log.info("Admin {} tiếp tục phòng {}", adminId, auctionId);
            }
            return ok;
        } finally {
            lock.unlock();
        }
    }
    public boolean cancelAuction(int auctionId, int adminId, String reason) {
        ReentrantLock lock = auctionLocks.computeIfAbsent(auctionId, k -> new ReentrantLock(true));
        lock.lock();
        try {
            Auction auction = auctionCache.get(auctionId);
            if (auction == null) auction = auctionDAO.findById(auctionId);
            if (auction == null) return false;

            AuctionStatus prev = auction.getStatus();
            if (prev == AuctionStatus.FINISHED || prev == AuctionStatus.CANCELED) return false;

            auction.setStatus(AuctionStatus.CANCELED);
            boolean ok = auctionDAO.updateStatus(auctionId, AuctionStatus.CANCELED);
            if (ok) {
                // Xóa khỏi cache vì phòng đã đóng hẳn
                auctionCache.remove(auctionId);
                auctionLocks.remove(auctionId);
                log.info("Admin {} HỦY phòng {} (trước: {}) | Lý do: {}", adminId, auctionId, prev, reason);
            }
            return ok;
        } finally {
            lock.unlock();
        }
    }
}