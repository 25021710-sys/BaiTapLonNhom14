package com.auction.server.service;

import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.BidDAO;
import com.auction.server.network.ClientHandler;
import com.auction.server.model.Auction;
import com.auction.server.model.AuctionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * 3. Scheduler: 1 giây/lần kiểm tra phiên hết giờ → đóng tự động
 */
public class AuctionManager {
    private static final Logger log = LoggerFactory.getLogger(AuctionManager.class);

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static volatile AuctionManager instance;

    private AuctionManager() {
        loadActiveAuctions();
        startScheduler();
        log.info("AuctionManager khởi tạo thành công.");
    }

    public static AuctionManager getInstance() {
        if (instance == null) {
            synchronized (AuctionManager.class) {
                if (instance == null) {
                    instance = new AuctionManager();
                }
            }
        }
        return instance;
    }

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final AuctionDAO    auctionDAO    = new AuctionDAO();
    private final BidDAO        bidDAO        = new BidDAO();
    private final AutoBidEngine autoBidEngine = new AutoBidEngine(bidDAO);
    private final AuctionService auctionService = new AuctionService(auctionDAO, bidDAO, autoBidEngine);

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

    /**
     * Đăng ký client nhận realtime update của một phiên.
     */
    public void subscribe(int auctionId, ClientHandler handler) {
        subscribers.computeIfAbsent(auctionId,
                k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(handler);
        log.debug("Client subscribe auction={}", auctionId);
    }

    /**
     * Hủy đăng ký (khi client rời phòng hoặc ngắt kết nối).
     */
    public void unsubscribe(int auctionId, ClientHandler handler) {
        Set<ClientHandler> set = subscribers.get(auctionId);
        if (set != null) set.remove(handler);
    }

    /**
     * Gửi thông báo bid mới đến TẤT CẢ client đang xem phiên đó.
     * Payload JSON: {"type":"BID_UPDATE","auctionId":1,"amount":150000,"bidderId":3,"endTime":"..."}
     */
    public void broadcastBidUpdate(int auctionId, double amount, int bidderId, String endTime) {
        String payload = String.format(
                "{\"type\":\"BID_UPDATE\",\"auctionId\":%d,\"amount\":%.2f,\"bidderId\":%d,\"endTime\":\"%s\"}",
                auctionId, amount, bidderId, endTime);
        broadcast(auctionId, payload);
    }

    /**
     * Gửi thông báo phiên kết thúc.
     */
    public void broadcastAuctionEnd(int auctionId, int winnerId, double finalPrice) {
        String payload = String.format(
                "{\"type\":\"AUCTION_END\",\"auctionId\":%d,\"winnerId\":%d,\"finalPrice\":%.2f}",
                auctionId, winnerId, finalPrice);
        broadcast(auctionId, payload);
    }

    // ── PRIVATE ───────────────────────────────────────────────────────────────

    private void broadcast(int auctionId, String jsonPayload) {
        Set<ClientHandler> set = subscribers.get(auctionId);
        if (set == null || set.isEmpty()) return;

        int count = 0;
        for (ClientHandler handler : set) {
            try {
                handler.sendNotification(jsonPayload);
                count++;
            } catch (Exception e) {
                log.warn("Broadcast thất bại tới 1 client, auction={}: {}", auctionId, e.getMessage());
                set.remove(handler); // Xóa client lỗi khỏi danh sách
            }
        }
        log.debug("Broadcast auction={} đến {} client(s)", auctionId, count);
    }

    private void loadActiveAuctions() {
        auctionService.loadActiveAuctions();
    }

    private void startScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkExpiredAuctions();
            } catch (Exception e) {
                log.error("Lỗi scheduler kiểm tra phiên hết giờ", e);
            }
        }, 5, 5, TimeUnit.SECONDS); // kiểm tra mỗi 5 giây
    }

    private void checkExpiredAuctions() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        // Lấy các phiên RUNNING chưa được đóng
        auctionDAO.findActiveAuctions().forEach(auction -> {
            if (now.isAfter(auction.getEndTime())
                    && auction.getStatus() == AuctionStatus.RUNNING) {
                auctionService.closeAuction(auction);
                broadcastAuctionEnd(auction.getId(),
                        auction.getHighestBidderId(),
                        auction.getCurrentPrice() != null
                                ? auction.getCurrentPrice().doubleValue() : 0.0);
            }
        });
    }
}
