package com.auction.server.service;

import com.auction.common.dto.AuctionUpdateDTO;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.BidDAO;
import com.auction.server.network.ClientHandler;
import com.auction.server.model.Auction;
import com.auction.server.model.AuctionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * AuctionManager – Singleton quản lý trung tâm:
 *
 * 1. Singleton (thread-safe, double-checked locking)
 * 2. Observer Pattern: quản lý danh sách ClientHandler đang xem mỗi phiên,
 *    broadcast event khi có bid mới / phiên kết thúc
 * 3. Scheduler: 5 giây/lần kiểm tra phiên hết giờ → đóng tự động
 */
public class AuctionManager {
    private static final Logger log = LoggerFactory.getLogger(AuctionManager.class);

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static volatile AuctionManager instance;

    private AuctionManager() {}

    public static AuctionManager getInstance() {
        if (instance == null) {
            synchronized (AuctionManager.class) {
                if (instance == null) {
                    instance = new AuctionManager();
                    instance.init();
                }
            }
        }
        return instance;
    }

    // ── Dependencies ──────────────────────────────────────────────────────────
    private AuctionDAO    auctionDAO;
    private BidDAO        bidDAO;
    private AutoBidEngine autoBidEngine;
    private AuctionService auctionService;

    private void init() {
        this.auctionDAO    = new AuctionDAO();
        this.bidDAO        = new BidDAO();
        this.autoBidEngine = new AutoBidEngine(bidDAO);
        this.auctionService = new AuctionService(auctionDAO, bidDAO, autoBidEngine);

        // Inject circular references
        this.autoBidEngine.setAuctionService(auctionService);
        this.auctionService.setAuctionManager(this);

        auctionService.loadActiveAuctions();
        startScheduler();
        log.info("AuctionManager khởi tạo thành công.");
    }

    // ── Observer: auctionId → Set<ClientHandler> đang subscribe ──────────────
    private final ConcurrentHashMap<Integer, Set<ClientHandler>> subscribers
            = new ConcurrentHashMap<>();

    // ── Scheduler kiểm tra phiên hết giờ ─────────────────────────────────────
    private final ScheduledExecutorService scheduler
            = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "auction-scheduler");
        t.setDaemon(true);
        return t;
    });

    // ── PUBLIC API ────────────────────────────────────────────────────────────

    public AuctionService getAuctionService() { return auctionService; }

    /** Trả về AutoBidEngine đang thực sự chạy (dùng cho AutoBid registration). */
    public AutoBidEngine getAutoBidEngine() {
        return autoBidEngine; // trả về instance đang dùng thật
    }
    /**
     * Đăng ký client nhận realtime update của một phiên.
     */
    public void subscribe(int auctionId, ClientHandler handler) {
        subscribers.computeIfAbsent(auctionId,
                k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(handler);
        log.debug("Client subscribe auction={}", auctionId);
        broadcastParticipantCount(auctionId);
    }

    public void unsubscribe(int auctionId, ClientHandler handler) {
        Set<ClientHandler> set = subscribers.get(auctionId);
        if (set != null) {
            set.remove(handler);
            broadcastParticipantCount(auctionId);
        }
    }

    public int getParticipantCount(int auctionId) {
        Set<ClientHandler> set = subscribers.get(auctionId);
        return set == null ? 0 : set.size();
    }

    private void broadcastParticipantCount(int auctionId) {
        int count = getParticipantCount(auctionId);
        AuctionUpdateDTO update = new AuctionUpdateDTO(
                auctionId,
                AuctionUpdateDTO.UpdateType.PARTICIPANT_CHANGED,
                null, 0, null, null,
                "Số người tham gia: " + count,
                count
        );
        broadcastUpdate(auctionId, update);
    }

    /**
     * Broadcast AuctionUpdateDTO đến tất cả client đang xem phiên.
     * Đây là phương thức chính cho Observer pattern.
     */
    public void broadcastUpdate(int auctionId, AuctionUpdateDTO update) {
        Set<ClientHandler> set = subscribers.get(auctionId);
        if (set == null || set.isEmpty()) return;

        int count = 0;
        for (ClientHandler handler : set) {
            try {
                handler.onAuctionUpdate(update);
                count++;
            } catch (Exception e) {
                log.warn("Broadcast thất bại tới 1 client, auction={}: {}", auctionId, e.getMessage());
                set.remove(handler);
            }
        }
        log.debug("Broadcast AuctionUpdateDTO auction={} đến {} client(s)", auctionId, count);
    }

    /**
     * Gửi thông báo phiên kết thúc (legacy string format cho backward compat).
     */
    // Thêm method mới nhận thêm winnerUsername
    public void broadcastAuctionEnd(int auctionId, int winnerId,
                                    double finalPrice, String winnerUsername) {
        String message;
        if (winnerId == 0) {
            message = "Phiên đấu giá kết thúc — Không có người thắng (không đạt giá sàn).";
        } else {
            message = String.format("Phiên kết thúc! 🏆 Người thắng: %s — Giá: %,.0f VND",
                    winnerUsername, finalPrice);
        }

        AuctionUpdateDTO update = new AuctionUpdateDTO(
                auctionId,
                AuctionUpdateDTO.UpdateType.AUCTION_ENDED,
                BigDecimal.valueOf(finalPrice),
                winnerId,
                winnerUsername,   // ← có tên rồi
                null,
                message
        );
        broadcastUpdate(auctionId, update);
    }

    // Giữ method cũ để không break code khác
    public void broadcastAuctionEnd(int auctionId, int winnerId, double finalPrice) {
        broadcastAuctionEnd(auctionId, winnerId, finalPrice, "");
    }

    // ── PRIVATE ───────────────────────────────────────────────────────────────

    private void startScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkExpiredAuctions();
            } catch (Exception e) {
                log.error("Lỗi scheduler kiểm tra phiên hết giờ", e);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private void checkExpiredAuctions() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        auctionDAO.findActiveAuctions().forEach(auction -> {
            if (now.isAfter(auction.getEndTime())
                    && (auction.getStatus() == AuctionStatus.RUNNING
                    || auction.getStatus() == AuctionStatus.OPEN)) {

                // Lưu thông tin trước khi closeAuction() có thể reset winnerId
                int winnerId = auction.getHighestBidderId();
                double finalPrice = auction.getCurrentPrice() != null
                        ? auction.getCurrentPrice().doubleValue() : 0.0;

                // Lấy tên người thắng từ DB trước
                String winnerUsername = "";
                if (winnerId != 0) {
                    try {
                        com.auction.server.dao.UserDAO userDAO =
                                new com.auction.server.dao.UserDAO();
                        com.auction.server.model.User winner = userDAO.findById(winnerId);
                        if (winner != null) winnerUsername = winner.getUsername();
                    } catch (Exception e) {
                        log.warn("Không lấy được tên winner auctionId={}", auction.getId());
                    }
                }

                // Đóng phiên (có thể reset winnerId nếu không đạt reserve)
                auctionService.closeAuction(auction);

                // Broadcast với thông tin đã lưu trước
                broadcastAuctionEnd(auction.getId(), winnerId, finalPrice, winnerUsername);
                log.info("Scheduler đóng phiên {} — winner={} ({}), giá={}",
                        auction.getId(), winnerUsername, winnerId, finalPrice);
            }
        });
    }
}