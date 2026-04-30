package com.auction.server.service;

import com.auction.server.model.AutoBidConfig;
import com.auction.server.dao.BidDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

public class AutoBidEngine {
    private static final Logger logger= LoggerFactory.getLogger(AutoBidEngine.class);
    private final BidDAO bidDAO;
    private final ConcurrentHashMap<Integer, PriorityQueue<AutoBidConfig>> autoBidQueues
            = new ConcurrentHashMap<>();

    public AutoBidEngine(BidDAO bidDAO) {
        this.bidDAO = bidDAO;
    }
    public synchronized void registerAutoBid(AutoBidConfig config) {
        bidDAO.saveAutoBidConfig(config);

        autoBidQueues.computeIfAbsent(config.getAuctionId(),
                k -> new PriorityQueue<>()).add(config);

        logger.info("Đã đăng ký auto-bid: bidder={}, auction={}, maxBid={}",
                config.getBidderUsername(), config.getAuctionId(), config.getMaxBid());
    }
    public synchronized void cancelAutoBid(int bidderId, int auctionId) {
        bidDAO.deactivateAutoBid(bidderId, auctionId);
        PriorityQueue<AutoBidConfig> queue = autoBidQueues.get(auctionId);
        if (queue != null) {
            queue.removeIf(cfg -> (cfg.getBidderId()==bidderId));
        }
        logger.info("Đã hủy auto-bid: bidder={}, auction={}", bidderId, auctionId);
    }
    public synchronized void loadFromDb(Integer auctionId) {
        List<AutoBidConfig> configs = this.bidDAO.findActiveConfigsByAuction(auctionId);
        PriorityQueue<AutoBidConfig> queue = new PriorityQueue(configs);
        this.autoBidQueues.put(auctionId, queue);
        logger.info("Đã load {} auto-bid configs cho auction: {}", configs.size(), auctionId);
    }
}
