package com.auction.server.service;

import com.auction.server.model.Auction;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.BidDAO;
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


}
