package com.auction.server.service;

import com.auction.common.dto.AuctionUpdateDTO;
import com.auction.common.request.BidRequest;
import com.auction.common.response.BidResponse;
import com.auction.server.model.*;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.BidDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.exception.AuctionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class AuctionService {
    private static final Logger log = LoggerFactory.getLogger(AuctionService.class);

    private final AuctionDAO auctionDAO;
    private final BidDAO bidDAO;
    private final AutoBidEngine autoBidEngine;
    private final UserDAO userDAO = new UserDAO();

    private static final int ANTI_SNIPE_THRESHOLD_SECONDS = 30;
    private static final int ANTI_SNIPE_EXTEND_SECONDS    = 60;

    private final ConcurrentHashMap<Integer, ReentrantLock> auctionLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Auction> auctionCache = new ConcurrentHashMap<>();

    // Reference đến AuctionManager để broadcast (set sau khi init)
    private AuctionManager auctionManager;

    public AuctionService(AuctionDAO auctionDAO, BidDAO bidDAO, AutoBidEngine autoBidEngine) {
        this.auctionDAO = auctionDAO;
        this.bidDAO = bidDAO;
        this.autoBidEngine = autoBidEngine;
    }

    public void setAuctionManager(AuctionManager manager) {
        this.auctionManager = manager;
    }

    // ── CREATE AUCTION ────────────────────────────────────────────────────────

    public Auction createAuction(int itemId, int sellerId,
                                 BigDecimal startingPrice,
                                 BigDecimal reservePrice,
                                 LocalDateTime startTime,
                                 LocalDateTime endTime) {
        if (endTime.isBefore(startTime))
            throw new AuctionException("INVALID_TIME", "Thời gian kết thúc phải sau thời gian bắt đầu");
        if (startingPrice.compareTo(BigDecimal.ZERO) <= 0)
            throw new AuctionException("INVALID_PRICE", "Giá khởi điểm phải lớn hơn 0");

        Auction auction = new Auction(itemId, sellerId, startingPrice, startTime, endTime);
        auction.setReservePrice(reservePrice);
        auction.setCurrentPrice(startingPrice);
        auction.setStatus(AuctionStatus.PENDING); // cần admin duyệt
        auctionDAO.saveAuction(auction);

        log.info("Tạo phiên đấu giá: {} | Item: {}", auction.getId(), itemId);
        return auction;
    }

    // ── PLACE BID ─────────────────────────────────────────────────────────────

    public BidResponse placeBid(BidRequest request) {
        int auctionId = Integer.parseInt(request.getAuctionId());
        int userId = request.getUserId();
        BigDecimal bidAmount = request.getAmount();

        ReentrantLock lock = auctionLocks.computeIfAbsent(auctionId, k -> new ReentrantLock(true));
        lock.lock();

        try {
            Auction auction;
            try {
                auction = getAuction(auctionId);
            } catch (AuctionException e) {
                return new BidResponse(false, "Phiên đấu giá không tồn tại!", BigDecimal.ZERO);
            }

            BigDecimal currentPrice = auction.getCurrentPrice() != null
                    ? auction.getCurrentPrice() : auction.getStartingPrice();

            // Kiểm tra trạng thái phiên
            AuctionStatus status = auction.getStatus();
            if (status != AuctionStatus.OPEN && status != AuctionStatus.RUNNING) {
                return new BidResponse(false,
                        "Giao dịch bị từ chối: Phiên đấu giá không trong trạng thái mở.", currentPrice);
            }

            // Kiểm tra thời gian
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(auction.getStartTime()) || now.isAfter(auction.getEndTime())) {
                return new BidResponse(false,
                        "Giao dịch bị từ chối: Phiên đấu giá không trong thời gian hoạt động.", currentPrice);
            }

            // Kiểm tra mức giá
            if (bidAmount.compareTo(currentPrice) <= 0) {
                return new BidResponse(false,
                        "Giao dịch bị từ chối: Mức giá đề xuất phải cao hơn giá hiện tại (" + currentPrice + ").", currentPrice);
            }

            // Kiểm tra không tự đấu với chính mình
            if (auction.getHighestBidderId() == userId) {
                return new BidResponse(false,
                        "Giao dịch bị từ chối: Bạn đang dẫn đầu phiên này.", currentPrice);
            }

            // Kiểm tra tài chính
            User user;
            try {
                user = userDAO.findById(userId);
            } catch (Exception e) {
                return new BidResponse(false, "Lỗi kiểm tra tài khoản.", currentPrice);
            }
            if (user == null || user.getBalance().compareTo(bidAmount) < 0) {
                return new BidResponse(false,
                        "Giao dịch bị từ chối: Tài khoản không đủ để thực hiện.", currentPrice);
            }

            // Hoàn tiền người dẫn đầu trước đó
            int previousBidderId = auction.getHighestBidderId();
            if (previousBidderId != 0 && previousBidderId != userId) {
                try {
                    User previousBidder = userDAO.findById(previousBidderId);
                    if (previousBidder != null) {
                        previousBidder.setBalance(previousBidder.getBalance().add(currentPrice));
                        userDAO.updateBalance(previousBidder.getId(), previousBidder.getBalance());
                    }
                } catch (Exception e) {
                    log.warn("Lỗi hoàn tiền cho previousBidder {}: {}", previousBidderId, e.getMessage());
                }
            }

            // Trừ tiền người đặt
            try {
                user.setBalance(user.getBalance().subtract(bidAmount));
                userDAO.updateBalance(user.getId(), user.getBalance());
            } catch (Exception e) {
                return new BidResponse(false, "Lỗi cập nhật tài khoản.", currentPrice);
            }

            // Cập nhật phiên đấu giá
            auction.setCurrentPrice(bidAmount);
            auction.setHighestBidderId(userId);
            auction.setStatus(AuctionStatus.RUNNING);
            auctionDAO.updateAuction(auction);
            auctionCache.put(auctionId, auction);

            // Lưu lịch sử
            BidTransaction transaction = new BidTransaction(0, now, auctionId, userId, bidAmount, false);
            bidDAO.saveBid(transaction);

            // Anti-sniping
            boolean extended = checkAndExtend(auction);

            // Broadcast update đến tất cả client đang xem phiên
            if (auctionManager != null) {
                String username = user.getUsername();
                AuctionUpdateDTO update = new AuctionUpdateDTO(
                        auctionId,
                        extended ? AuctionUpdateDTO.UpdateType.AUCTION_EXTENDED : AuctionUpdateDTO.UpdateType.BID_PLACED,
                        bidAmount,
                        userId,
                        username,
                        auction.getEndTime(),
                        extended ? "Phiên được gia hạn do có bid trong 30 giây cuối" : "Bid mới!"
                );
                auctionManager.broadcastUpdate(auctionId, update);
            }

            // Kích hoạt auto-bid của người khác (nếu có)
            BidTransaction autoBidTx = autoBidEngine.triggerAutoBid(auctionId, userId, bidAmount);
            if (autoBidTx != null && auctionManager != null) {
                // Broadcast auto-bid update
                AuctionUpdateDTO autoUpdate = new AuctionUpdateDTO(
                        auctionId,
                        AuctionUpdateDTO.UpdateType.BID_PLACED,
                        autoBidTx.getAmount(),
                        autoBidTx.getBidderId(),
                        "auto-bid",
                        auction.getEndTime(),
                        "Auto-bid!"
                );
                auctionManager.broadcastUpdate(auctionId, autoUpdate);
            }

            log.info("Bid thành công: UserID {} đặt {} VND cho AuctionID {}", userId, bidAmount, auctionId);
            return new BidResponse(true, "Giao dịch thành công. Mức giá mới đã được ghi nhận.", bidAmount);

        } catch (Exception e) {
            log.error("Lỗi đặt cược (AuctionID: {}): {}", auctionId, e.getMessage(), e);
            return new BidResponse(false, "Lỗi Server, vui lòng thử lại sau.", BigDecimal.ZERO);
        } finally {
            lock.unlock();
        }
    }

    // ── GET AUCTION ───────────────────────────────────────────────────────────

    public Auction getAuction(int auctionId) {
        return auctionCache.computeIfAbsent(auctionId, id -> {
            Auction a = auctionDAO.findById(id);
            if (a == null) throw new AuctionException("NOT_FOUND", "Phiên đấu giá không tồn tại: " + id);
            auctionLocks.computeIfAbsent(id, k -> new ReentrantLock(true));
            return a;
        });
    }

    public void loadActiveAuctions() {
        List<Auction> active = auctionDAO.findActiveAuctions();
        for (Auction a : active) {
            auctionCache.put(a.getId(), a);
            auctionLocks.put(a.getId(), new ReentrantLock(true));
            autoBidEngine.loadFromDb(a.getId());
        }
        log.info("Đã load {} phiên đấu giá vào cache", active.size());
    }

    public List<Auction> getActiveAuctions() {
        return auctionDAO.findActiveAuctions();
    }

    public List<Auction> getPendingAuctions() {
        return auctionDAO.findPendingAuctions();
    }

    // ── AUTO BID (internal, không gọi triggerAutoBid lại) ────────────────────

    public BidTransaction placeAutoBid(int auctionId, int bidderId, BigDecimal amount) {
        Auction auction = getAuction(auctionId);

        // Hoàn tiền người dẫn đầu cũ
        int previousBidderId = auction.getHighestBidderId();
        if (previousBidderId != 0 && previousBidderId != bidderId) {
            try {
                User prev = userDAO.findById(previousBidderId);
                BigDecimal prevPrice = auction.getCurrentPrice();
                if (prev != null && prevPrice != null) {
                    prev.setBalance(prev.getBalance().add(prevPrice));
                    userDAO.updateBalance(prev.getId(), prev.getBalance());
                }
            } catch (Exception e) {
                log.warn("Lỗi hoàn tiền auto-bid previousBidder {}: {}", previousBidderId, e.getMessage());
            }
        }

        // Trừ tiền auto-bidder
        try {
            User bidder = userDAO.findById(bidderId);
            if (bidder != null && bidder.getBalance().compareTo(amount) >= 0) {
                bidder.setBalance(bidder.getBalance().subtract(amount));
                userDAO.updateBalance(bidderId, bidder.getBalance());
            } else {
                log.warn("Auto-bidder {} không đủ tiền: balance={}", bidderId,
                        bidder != null ? bidder.getBalance() : "null");
                return null;
            }
        } catch (Exception e) {
            log.warn("Lỗi trừ tiền auto-bidder {}: {}", bidderId, e.getMessage());
            return null;
        }

        BidTransaction bid = new BidTransaction();
        bid.setAuctionId(auctionId);
        bid.setBidderId(bidderId);
        bid.setAmount(amount);
        bid.setAutoBid(true);
        bid.setCreatedAt(LocalDateTime.now());
        bidDAO.saveBid(bid);

        auctionDAO.updateBidPrice(auctionId, bidderId, amount);
        auction.setCurrentPrice(amount);
        auction.setHighestBidderId(bidderId);

        checkAndExtend(auction);
        log.info("Auto-bid: auctionId={} bidderId={} amount={}", auctionId, bidderId, amount);
        return bid;
    }

    // ── CLOSE AUCTION ─────────────────────────────────────────────────────────

    public void closeAuction(Auction auction) {
        auction.setStatus(AuctionStatus.FINISHED);
        auctionDAO.updateStatus(auction.getId(), AuctionStatus.FINISHED);
        auctionCache.remove(auction.getId()); // xóa khỏi cache
        log.info("Phiên {} kết thúc. Người thắng: bidderId={}, giá={}",
                auction.getId(), auction.getHighestBidderId(), auction.getCurrentPrice());
    }

    // ── ADMIN ─────────────────────────────────────────────────────────────────

    public boolean approveAuction(int auctionId, int adminId) {
        Auction auction = auctionDAO.findById(auctionId);
        if (auction == null) return false;
        auction.setStatus(AuctionStatus.OPEN);
        auctionDAO.updateStatus(auctionId, AuctionStatus.OPEN);
        // Load vào cache
        auctionCache.put(auctionId, auction);
        auctionLocks.computeIfAbsent(auctionId, k -> new ReentrantLock(true));
        autoBidEngine.loadFromDb(auctionId);
        log.info("Admin {} duyệt phiên đấu giá {}", adminId, auctionId);
        return true;
    }

    public boolean rejectAuction(int auctionId, int adminId, String reason) {
        Auction auction = auctionDAO.findById(auctionId);
        if (auction == null) return false;
        auction.setStatus(AuctionStatus.CANCELED);
        auctionDAO.updateStatus(auctionId, AuctionStatus.CANCELED);
        log.info("Admin {} từ chối phiên đấu giá {} | Lý do: {}", adminId, auctionId, reason);
        return true;
    }

    // ── ANTI-SNIPING ──────────────────────────────────────────────────────────

    /**
     * Nếu bid trong X giây cuối → gia hạn thêm Y giây.
     * @return true nếu đã gia hạn
     */
    private boolean checkAndExtend(Auction auction) {
        long secondsLeft = java.time.Duration.between(LocalDateTime.now(), auction.getEndTime()).getSeconds();
        if (secondsLeft > 0 && secondsLeft < ANTI_SNIPE_THRESHOLD_SECONDS) {
            LocalDateTime newEnd = auction.getEndTime().plusSeconds(ANTI_SNIPE_EXTEND_SECONDS);
            auction.setEndTime(newEnd);
            auction.setExtensionCount(auction.getExtensionCount() + 1);
            auctionDAO.extendEndTime(auction.getId(), newEnd);
            log.info("Anti-snipe: auction {} gia hạn đến {}", auction.getId(), newEnd);
            return true;
        }
        return false;
    }
}

