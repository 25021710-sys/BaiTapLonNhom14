package com.auction.server.controller;

import com.auction.common.request.BidRequest;
import com.auction.common.response.BidResponse;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;

import com.auction.server.model.AutoBidConfig;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.BidDAO;
import com.auction.server.service.AuctionService;
import com.auction.server.service.AutoBidEngine;

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

            case "BID_PLACE":
                handlePlaceBid(in, out);
                break;
            default:
                System.out.println("Lệnh không hỗ trợ: " + action);
        }
    }

    // Hàm xử lý lệnh Đặt cược
    private void handlePlaceBid(ObjectInputStream in, ObjectOutputStream out) {
        try {
            // nhận request
            BidRequest request = (BidRequest) in.readObject();
            System.out.println("[Server Nhận]: User " + request.getUserId() +
                    " đặt " + request.getAmount() +
                    " VNĐ cho phiên số " + request.getAuctionId());

            BidResponse response = auctionService.placeBid(request);

            // trả kết quả về cho Client
            out.writeObject(response);
            out.flush();
            System.out.println("[Server Trả]: Đã gửi kết quả Bid về cho Client.");

        } catch (Exception e) {
            System.err.println("Lỗi xử lý đặt cược: " + e.getMessage());
            e.printStackTrace();

            try {
                // báo lỗi gửi về cho Client
                BidResponse errorResponse = new BidResponse(false, "Lỗi Server: " + e.getMessage(), BigDecimal.ZERO);
                out.writeObject(errorResponse);
                out.flush();
            } catch (Exception ioException) {
                System.err.println("Lỗi");
            }
        }
    }
}