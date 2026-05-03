//package com.auction.server.service;
//
//import com.auction.server.dao.AuctionDAO;
//import com.auction.server.dao.BidDAO;
//import com.auction.server.dao.UserDao;
//import com.auction.server.network.ClientHandler;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.Set;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.Executors;
//import java.util.concurrent.ScheduledExecutorService;
//
//public class AuctionManager {
//    private static final Logger log = LoggerFactory.getLogger(AuctionManager.class);
//    private static volatile AuctionManager instance;
//    private final UserDao userDAO;
//    private final AuctionService auctionService;
//    private final AutoBidEngine autoBidEngine;
//    private final ConcurrentHashMap<String, Set<ClientHandler>> subscribers
//            = new ConcurrentHashMap<>();
//    private final ScheduledExecutorService scheduler
//            = Executors.newSingleThreadScheduledExecutor(r -> {
//        Thread t = new Thread(r, "auction-scheduler");
//        t.setDaemon(true);
//        return t;
//    });
//    private AuctionManager() {
//        UserDao userDao = new UserDao();
//        AuctionDAO auctionDao = new AuctionDAO();
//        BidDAO bidDao = new BidDAO();
//
//        this.autoBidEngine = new AutoBidEngine(bidDao);
//        this.userDAO = new UserDao();
//        this.auctionService = new AuctionService(
//                auctionDao, bidDao, autoBidEngine, userDAO);
//
//        startScheduler();
//        log.info("AuctionManager khởi tạo thành công.");
//    }
//}
