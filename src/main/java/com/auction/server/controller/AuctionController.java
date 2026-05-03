package com.auction.server.controller;

import com.auction.common.dto.BidRequest;
import com.auction.server.model.AutoBidConfig;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.BidDAO;
import com.auction.server.service.AuctionService;
import com.auction.server.service.AutoBidEngine;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class AuctionController {

    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final BidDAO bidDAO = new BidDAO();
    private final AutoBidEngine autoBidEngine = new AutoBidEngine(bidDAO);
    private final AuctionService auctionService = new AuctionService(auctionDAO, bidDAO, autoBidEngine);

    public void processRequest(String action, ObjectInputStream in, ObjectOutputStream out) throws Exception {
        switch (action) {
            case "AUTOBID_REGISTER":
                AutoBidConfig config = (AutoBidConfig) in.readObject();
                autoBidEngine.registerAutoBid(config);
                out.writeObject("AUTOBID_REGISTER_SUCCESS"); // Có thể đổi thành Response DTO
                out.flush();
                break;

            case "AUTOBID_CANCEL":
                int bidderId = in.readInt();
                int auctionId = in.readInt();
                autoBidEngine.cancelAutoBid(bidderId, auctionId);
                out.writeObject("AUTOBID_CANCEL_SUCCESS");
                out.flush();
                break;

            // case "BID_PLACE"
        }
    }
}