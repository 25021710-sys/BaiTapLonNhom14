//package com.auction.server.service;
//
//import com.auction.server.model.AutoBidConfig;
//import com.auction.server.model.BidTransaction;
//import com.auction.server.dao.BidDAO;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.math.BigDecimal;
//import java.util.List;
//import java.util.PriorityQueue;
//import java.util.concurrent.ConcurrentHashMap;
//
///**
// * AutoBidEngine – xử lý đấu giá tự động (Auto-Bidding).
// *
// * Logic:
// *  - Mỗi phiên có 1 PriorityQueue<AutoBidConfig> sắp xếp theo:
// *      maxBid giảm dần → registeredAt tăng dần (đăng ký sớm ưu tiên hơn).
// *  - Khi có bid mới, gọi triggerAutoBid() để hệ thống tự phản ứng.
// *  - Không vượt maxBid, không để 2 người cùng thắng.
// */
//public class AutoBidEngine {
//    private static final Logger logger = LoggerFactory.getLogger(AutoBidEngine.class);
//
//    private final BidDAO bidDAO;
//    // auctionId → PriorityQueue các auto-bid config
//    private final ConcurrentHashMap<Integer, PriorityQueue<AutoBidConfig>> autoBidQueues
//            = new ConcurrentHashMap<>();
//
//    // Reference đến AuctionService để thực thi auto-bid (được set sau khi tạo)
//    private AuctionService auctionService;
//
//    public AutoBidEngine(BidDAO bidDAO) {
//        this.bidDAO = bidDAO;
//    }
//
//    public AutoBidEngine() {
//        this.bidDAO = new BidDAO();
//    }
//
//    /** Inject AuctionService sau khi cả hai đã được tạo (tránh circular dependency) */
//    public void setAuctionService(AuctionService auctionService) {
//        this.auctionService = auctionService;
//    }
//
//    /** Đăng ký hoặc cập nhật auto-bid config */
//    public synchronized void registerAutoBid(AutoBidConfig config) {
//        bidDAO.saveAutoBidConfig(config);
//
//        PriorityQueue<AutoBidConfig> queue = autoBidQueues
//                .computeIfAbsent(config.getAuctionId(), k -> new PriorityQueue<>());
//        // Xóa config cũ của cùng bidder (nếu có) trước khi thêm mới
//        queue.removeIf(cfg -> cfg.getBidderId() == config.getBidderId());
//        queue.add(config);
//
//        logger.info("Đã đăng ký auto-bid: bidder={}, auction={}, maxBid={}",
//                config.getBidderUsername(), config.getAuctionId(), config.getMaxBid());
//    }
//
//    /** Hủy auto-bid */
//    public synchronized void cancelAutoBid(int bidderId, int auctionId) {
//        bidDAO.deactivateAutoBid(bidderId, auctionId);
//        PriorityQueue<AutoBidConfig> queue = autoBidQueues.get(auctionId);
//        if (queue != null) {
//            queue.removeIf(cfg -> cfg.getBidderId() == bidderId);
//        }
//        logger.info("Đã hủy auto-bid: bidder={}, auction={}", bidderId, auctionId);
//    }
//
//    /** Load auto-bid configs từ DB vào memory khi server khởi động */
//    public synchronized void loadFromDb(Integer auctionId) {
//        List<AutoBidConfig> configs = this.bidDAO.findActiveConfigsByAuction(auctionId);
//        PriorityQueue<AutoBidConfig> queue = new PriorityQueue<>(configs);
//        this.autoBidQueues.put(auctionId, queue);
//        logger.info("Đã load {} auto-bid configs cho auction: {}", configs.size(), auctionId);
//    }
//
//    /**
//     * Kích hoạt auto-bid sau khi có 1 bid thủ công thành công.
//     * Được gọi từ AuctionService BÊN TRONG ReentrantLock của phiên đó.
//     *
//     * FIX CHAIN REACTION: sau khi auto-bid A thắng, nếu có auto-bid B cao hơn
//     * thì B cũng phải phản ứng lại. Vòng lặp tiếp tục cho đến khi:
//     *   - Không còn ai muốn/có thể bid cao hơn
//     *   - Người đứng đầu queue chính là người vừa bid (không tự outbid mình)
//     *
//     * @param auctionId        phiên đấu giá
//     * @param triggerBidderId  người vừa đặt (auto-bid của họ không tự phản ứng với chính mình)
//     * @param currentPrice     giá hiện tại sau bid vừa rồi
//     * @return BidTransaction của auto-bid cuối cùng được kích hoạt, null nếu không có
//     */
//    public synchronized BidTransaction triggerAutoBid(int auctionId, int triggerBidderId,
//                                                      BigDecimal currentPrice) {
//        if (auctionService == null) return null;
//
//        BidTransaction lastAutoBidTx = null;
//        int lastBidderId = triggerBidderId;
//        BigDecimal lastPrice = currentPrice;
//
//        // FIX: vòng lặp chain reaction thay vì chỉ kích hoạt 1 lần
//        while (true) {
//            PriorityQueue<AutoBidConfig> queue = autoBidQueues.get(auctionId);
//            if (queue == null || queue.isEmpty()) break;
//
//            AutoBidConfig top = queue.peek();
//            if (top == null) break;
//
//            // Không auto-bid cho chính người vừa bid (kể cả auto-bid trước đó)
//            if (top.getBidderId() == lastBidderId) break;
//
//            // Tính giá auto-bid tiếp theo
//            BigDecimal nextBid = top.calculateNextBid(lastPrice);
//            if (nextBid == null) {
//                // Config này đã hết hạn mức → loại khỏi queue và thử người tiếp theo
//                queue.poll();
//                bidDAO.deactivateAutoBid(top.getBidderId(), auctionId);
//                logger.info("Auto-bid config hết hạn mức: bidder={}, auction={}", top.getBidderId(), auctionId);
//                continue;
//            }
//
//            // Thực hiện auto-bid
//            try {
//                BidTransaction tx = auctionService.placeAutoBid(auctionId, top.getBidderId(), nextBid);
//                if (tx != null) {
//                    lastAutoBidTx = tx;
//                    lastBidderId = top.getBidderId();
//                    lastPrice = nextBid;
//                    logger.info("Auto-bid chain: bidder={} đặt {} cho auction={}",
//                            top.getBidderId(), nextBid, auctionId);
//                } else {
//                    // Auto-bidder không đủ tiền → loại khỏi queue, thử người tiếp
//                    queue.poll();
//                    bidDAO.deactivateAutoBid(top.getBidderId(), auctionId);
//                    logger.info("Auto-bid bị loại (không đủ tiền): bidder={}", top.getBidderId());
//                }
//            } catch (Exception e) {
//                logger.warn("Auto-bid thất bại: bidder={}, auction={}: {}",
//                        top.getBidderId(), auctionId, e.getMessage());
//                break;
//            }
//        }
//
//        return lastAutoBidTx;
//    }
//}
