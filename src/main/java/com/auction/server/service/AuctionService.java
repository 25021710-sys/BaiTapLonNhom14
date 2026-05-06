package com.auction.server.service;

import com.auction.common.request.BidRequest;
import com.auction.common.response.BidResponse;
import com.auction.server.model.Auction;
import com.auction.server.model.User;
import com.auction.server.model.BidTransaction;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.BidDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.exception.AuctionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class AuctionService {
    private static final Logger log = LoggerFactory.getLogger(AuctionService.class);

    private final AuctionDAO auctionDAO;
    private final BidDAO bidDAO;
    private final AutoBidEngine autoBidEngine;
    // UserDAO kiểm tra số dư tkhoan
    private final UserDAO userDAO = new UserDAO();

    private final ConcurrentHashMap<Integer, ReentrantLock> auctionLocks
            = new ConcurrentHashMap<Integer, ReentrantLock>();
    private final ConcurrentHashMap<Integer, Auction> auctionCache
            = new ConcurrentHashMap<Integer, Auction>();

    public AuctionService(AuctionDAO auctionDAO, BidDAO bidDAO,
                          AutoBidEngine autoBidEngine) {
        this.auctionDAO = auctionDAO;
        this.bidDAO = bidDAO;
        this.autoBidEngine = autoBidEngine;
    }

    public Auction createAuction(int itemId, int sellerId,
                                 BigDecimal startingPrice,
                                 LocalDateTime startTime,
                                 LocalDateTime endTime) {
        if (endTime.isBefore(startTime))
            throw new AuctionException("INVALID_TIME", "Thời gian kết thúc phải sau thời gian bắt đầu");
        if (startingPrice.compareTo(BigDecimal.ZERO) <= 0)
            throw new AuctionException("INVALID_PRICE", "Giá khởi điểm phải lớn hơn 0");

        Auction auction = new Auction(itemId, sellerId, startingPrice, startTime, endTime);
        auctionDAO.saveAuction(auction);

        auctionCache.put(auction.getId(), auction);
        auctionLocks.put(auction.getId(), new ReentrantLock(true)); // fair lock
        autoBidEngine.loadFromDb(auction.getId());

        log.info("Tạo phiên đấu giá: {} | Item: {}", auction.getId(), itemId);
        return auction;
    }

    public BidResponse placeBid(BidRequest request) {
        int auctionId = Integer.parseInt(request.getAuctionId());
        int userId = request.getUserId();
        BigDecimal bidAmount = request.getAmount();

        // TÌM VÀ KHÓA PHIÊN ĐẤU GIÁ
        ReentrantLock lock = auctionLocks.computeIfAbsent(auctionId, k -> new ReentrantLock(true));
        lock.lock();

        try {
            // TÌM KIẾM DỮ LIỆU (Ưu tiên lấy từ Cache, không có mới gọi Database)
            Auction auction = auctionCache.get(auctionId);
            if (auction == null) {
                auction = auctionDAO.findById(auctionId);
                if (auction == null) {
                    return new BidResponse(false, "Phiên đấu giá không tồn tại!", BigDecimal.ZERO);
                }
                auctionCache.put(auctionId, auction); // Nạp lại vào Cache
            }
            BigDecimal currentPrice = auction.getCurrentPrice();

            // KIỂM TRA THỜI GIAN
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(auction.getStartTime()) || now.isAfter(auction.getEndTime())) {
                return new BidResponse(false, "Giao dịch bị từ chối: Phiên đấu giá không trong trạng thái mở.", currentPrice);
            }

            // KIỂM TRA MỨC GIÁ
            if (bidAmount.compareTo(currentPrice) <= 0) {
                return new BidResponse(false, "Giao dịch bị từ chối: Mức giá đề xuất phải cao hơn giá hiện tại.", currentPrice);
            }

            // KIỂM TRA TÀI CHÍNH
            User user = userDAO.findById(userId);
            if (user == null || user.getBalance().compareTo(bidAmount) < 0) {
                return new BidResponse(false, "Giao dịch bị từ chối: Tài khoản không đủ để thực hiện.", currentPrice);
            }

            // THỰC THI GIAO DỊCH DATABASE

            // 1) Trừ tiền người vừa đặt cược
            user.setBalance(user.getBalance().subtract(bidAmount));
            userDAO.updateBalance(user.getId(), user.getBalance());

            // 2) Hoàn tiền cho người đang giữ Top 1 trước đó
            int previousBidderId = auction.getHighestBidderId();
            if (previousBidderId != 0 && previousBidderId != userId) {
                User previousBidder = userDAO.findById(previousBidderId);
                if (previousBidder != null) {
                    previousBidder.setBalance(previousBidder.getBalance().add(currentPrice));
                    userDAO.updateBalance(previousBidder.getId(), previousBidder.getBalance());
                }
            }

            // 3) Cập nhật thông tin phiên đấu giá (Cả Database và Cache)
            auction.setCurrentPrice(bidAmount);
            auction.setHighestBidderId(userId);
            auctionDAO.updateAuction(auction);
            auctionCache.put(auctionId, auction);

            // 4) lưu lịch sử đặt cược
            BidTransaction transaction = new BidTransaction(0, now, auctionId, userId, bidAmount, false); // id truyền 0 vì DB tự tăng
            bidDAO.saveBid(transaction);

            log.info("Giao dịch đặt cược thành công: UserID {} đặt {} VND cho AuctionID {}", userId, bidAmount, auctionId);
            return new BidResponse(true, "Giao dịch thành công. Mức giá mới đã được ghi nhận.", bidAmount);

        } catch (Exception e) {
            log.error("Lỗi đặt cược (AuctionID: {}): {}", auctionId, e.getMessage(), e);
            return new BidResponse(false, "Lỗi Server, vui lòng thử lại sau.", BigDecimal.ZERO);
        } finally {
            // GIẢI PHÓNG KHÓA
            lock.unlock();
        }
    }
}

