package com.auction.server.controller;

import com.auction.common.dto.AuctionDTO;
import com.auction.common.request.*;
import com.auction.common.response.*;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.BidDAO;
import com.auction.server.dao.ItemDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.model.*;
import com.auction.server.service.AuctionManager;
import com.auction.server.service.AuctionService;
import com.auction.server.network.ClientHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class AuctionController {
    private static final Logger log = LoggerFactory.getLogger(AuctionController.class);

    private final AuctionManager auctionManager = AuctionManager.getInstance();
    private final AuctionService auctionService = auctionManager.getAuctionService();
    private final ItemDAO itemDAO = new ItemDAO();
    private final UserDAO userDAO = new UserDAO();
    private final BidDAO bidDAO = new BidDAO();

    public void processRequest(String action, ObjectInputStream in, ObjectOutputStream out,
                               ClientHandler handler) throws Exception {
        switch (action) {

            case "BID_PLACE" -> handlePlaceBid(in, out);

            case "AUCTION_CREATE" -> handleCreateAuction(in, out);

            case "AUCTION_GET_ACTIVE" -> handleGetActiveAuctions(out);

            case "AUCTION_GET_PENDING_REQUESTS" -> handleGetPendingAuctions(in, out);

            case "AUCTION_APPROVE" -> handleApproveAuction(in, out);

            case "AUCTION_REJECT" -> handleRejectAuction(in, out);

            case "AUCTION_SUBSCRIBE" -> handleSubscribe(in, out, handler);

            case "AUCTION_UNSUBSCRIBE" -> handleUnsubscribe(in, out, handler);

            case "AUCTION_GET_BIDS" -> handleGetBidHistory(in, out);

            case "AUTOBID_REGISTER" -> handleRegisterAutoBid(in, out);

            case "AUTOBID_CANCEL" -> handleCancelAutoBid(in, out);

            default -> {
                log.warn("Action không hỗ trợ: {}", action);
                out.writeObject(new SimpleResponse(false, "Action không hỗ trợ: " + action));
                out.flush();
            }
        }
    }

    /** Overload để tương thích với ClientHandler cũ không truyền handler */
    public void processRequest(String action, ObjectInputStream in, ObjectOutputStream out) throws Exception {
        processRequest(action, in, out, null);
    }

    // ── HANDLERS ──────────────────────────────────────────────────────────────

    private void handlePlaceBid(ObjectInputStream in, ObjectOutputStream out) {
        try {
            BidRequest request = (BidRequest) in.readObject();
            log.info("Bid: User {} đặt {} cho auction {}", request.getUserId(),
                    request.getAmount(), request.getAuctionId());
            BidResponse response = auctionService.placeBid(request);
            out.writeObject(response);
            out.flush();
        } catch (Exception e) {
            log.error("Lỗi xử lý BID_PLACE: {}", e.getMessage(), e);
            try {
                out.writeObject(new BidResponse(false, "Lỗi Server: " + e.getMessage(), BigDecimal.ZERO));
                out.flush();
            } catch (Exception ignored) {}
        }
    }

    private void handleCreateAuction(ObjectInputStream in, ObjectOutputStream out) {
        try {
            CreateAuctionRequest req = (CreateAuctionRequest) in.readObject();

            // Tạo item trước
            Item item = createItemByCategory(req.getItemCategory());
            item.setName(req.getItemName());
            item.setDescription(req.getItemDescription());
            item.setStartingPrice(req.getStartingPrice());
            item.setSellerId(req.getSellerId());
            item.setCategory(ItemCategory.valueOf(req.getItemCategory()));

            ItemDAO iDao = new ItemDAO();
            boolean itemOk = iDao.insertItem(item);
            if (!itemOk) {
                out.writeObject(new CreateAuctionResponse(false, "Lỗi tạo sản phẩm", null));
                out.flush();
                return;
            }

            Auction auction = auctionService.createAuction(
                    item.getId(),
                    req.getSellerId(),
                    req.getStartingPrice(),
                    req.getReservePrice() != null ? req.getReservePrice() : req.getStartingPrice(),
                    req.getStartTime(),
                    req.getEndTime()
            );

            AuctionDTO dto = mapToDTO(auction, item, null);
            out.writeObject(new CreateAuctionResponse(true, "Tạo phiên đấu giá thành công, chờ admin duyệt.", dto));
            out.flush();
            log.info("Tạo auction mới: id={}, item={}", auction.getId(), item.getName());
        } catch (Exception e) {
            log.error("Lỗi AUCTION_CREATE: {}", e.getMessage(), e);
            try {
                out.writeObject(new CreateAuctionResponse(false, "Lỗi: " + e.getMessage(), null));
                out.flush();
            } catch (Exception ignored) {}
        }
    }

    private void handleGetActiveAuctions(ObjectOutputStream out) {
        try {
            List<Auction> auctions = auctionService.getActiveAuctions();
            List<AuctionDTO> dtos = new ArrayList<>();
            for (Auction a : auctions) {
                Item item = itemDAO.findById(a.getItemId());
                User seller = null;
                try { seller = userDAO.findById(a.getSellerId()); } catch (Exception ignored) {}
                dtos.add(mapToDTO(a, item, seller != null ? seller.getUsername() : ""));
            }
            out.writeObject(new AuctionListResponse(true, "OK", dtos));
            out.flush();
        } catch (Exception e) {
            log.error("Lỗi AUCTION_GET_ACTIVE: {}", e.getMessage(), e);
            try {
                out.writeObject(new AuctionListResponse(false, "Lỗi: " + e.getMessage(), null));
                out.flush();
            } catch (Exception ignored) {}
        }
    }

    private void handleGetPendingAuctions(ObjectInputStream in, ObjectOutputStream out) {
        try {
            in.readObject(); // đọc request object (GetPendingAuctionRequestsRequest)
            List<Auction> auctions = auctionService.getPendingAuctions();
            List<com.auction.common.dto.AdminAuctionRequestDTO> dtos = new ArrayList<>();
            for (Auction a : auctions) {
                Item item = itemDAO.findById(a.getItemId());
                User seller = null;
                try { seller = userDAO.findById(a.getSellerId()); } catch (Exception ignored) {}
                if (item == null) continue;

                com.auction.common.dto.AdminAuctionRequestDTO dto = new com.auction.common.dto.AdminAuctionRequestDTO();
                dto.setRequestId(a.getId());
                dto.setItemName(item.getName());
                dto.setSellerUsername(seller != null ? seller.getUsername() : "");
                dto.setItemCategory(item.getCategory().name());
                dto.setItemDescription(item.getDescription());
                dto.setApprovalStatus(a.getStatus().name());
                dto.setStartTime(a.getStartTime());
                dto.setEndTime(a.getEndTime());
                dto.setCreatedAt(a.getCreatedAt());
                dto.setStartingPrice(a.getStartingPrice());
                dto.setImageUrl("https://picsum.photos/seed/" + a.getId() + "/300/200");
                dtos.add(dto);
            }
            out.writeObject(new GetPendingAuctionRequestsResponse(true, "OK", dtos));
            out.flush();
        } catch (Exception e) {
            log.error("Lỗi AUCTION_GET_PENDING_REQUESTS: {}", e.getMessage(), e);
            try {
                out.writeObject(new GetPendingAuctionRequestsResponse(false, "Lỗi: " + e.getMessage(), null));
                out.flush();
            } catch (Exception ignored) {}
        }
    }

    private void handleApproveAuction(ObjectInputStream in, ObjectOutputStream out) {
        try {
            ApproveAuctionRequest req = (ApproveAuctionRequest) in.readObject();
            boolean ok = auctionService.approveAuction(req.getRequestId(), req.getAdminId());
            out.writeObject(new SimpleResponse(ok, ok ? "Đã duyệt phiên đấu giá." : "Không tìm thấy phiên."));
            out.flush();
        } catch (Exception e) {
            log.error("Lỗi AUCTION_APPROVE: {}", e.getMessage(), e);
            try { out.writeObject(new SimpleResponse(false, "Lỗi: " + e.getMessage())); out.flush(); } catch (Exception ignored) {}
        }
    }

    private void handleRejectAuction(ObjectInputStream in, ObjectOutputStream out) {
        try {
            RejectAuctionRequest req = (RejectAuctionRequest) in.readObject();
            boolean ok = auctionService.rejectAuction(req.getRequestId(), req.getAdminId(), req.getRejectReason());
            out.writeObject(new SimpleResponse(ok, ok ? "Đã từ chối phiên đấu giá." : "Không tìm thấy phiên."));
            out.flush();
        } catch (Exception e) {
            log.error("Lỗi AUCTION_REJECT: {}", e.getMessage(), e);
            try { out.writeObject(new SimpleResponse(false, "Lỗi: " + e.getMessage())); out.flush(); } catch (Exception ignored) {}
        }
    }

    private void handleSubscribe(ObjectInputStream in, ObjectOutputStream out, ClientHandler handler) {
        try {
            int auctionId = in.readInt();
            if (handler != null) {
                auctionManager.subscribe(auctionId, handler);
            }
            // Gửi lại thông tin phiên hiện tại
            Auction a = auctionService.getAuction(auctionId);
            Item item = itemDAO.findById(a.getItemId());
            User seller = null;
            try { seller = userDAO.findById(a.getSellerId()); } catch (Exception ignored) {}
            AuctionDTO dto = mapToDTO(a, item, seller != null ? seller.getUsername() : "");
            out.writeObject(new CreateAuctionResponse(true, "Subscribed", dto));
            out.flush();
        } catch (Exception e) {
            log.error("Lỗi AUCTION_SUBSCRIBE: {}", e.getMessage(), e);
            try { out.writeObject(new CreateAuctionResponse(false, "Lỗi: " + e.getMessage(), null)); out.flush(); } catch (Exception ignored) {}
        }
    }

    private void handleUnsubscribe(ObjectInputStream in, ObjectOutputStream out, ClientHandler handler) {
        try {
            int auctionId = in.readInt();
            if (handler != null) auctionManager.unsubscribe(auctionId, handler);
            out.writeObject(new SimpleResponse(true, "Unsubscribed"));
            out.flush();
        } catch (Exception e) {
            try { out.writeObject(new SimpleResponse(false, "Lỗi: " + e.getMessage())); out.flush(); } catch (Exception ignored) {}
        }
    }

    private void handleGetBidHistory(ObjectInputStream in, ObjectOutputStream out) {
        try {
            int auctionId = in.readInt();
            List<BidTransaction> bids = bidDAO.findByAuction(auctionId);
            out.writeObject(new BidHistoryResponse(true, "OK", bids));
            out.flush();
        } catch (Exception e) {
            log.error("Lỗi AUCTION_GET_BIDS: {}", e.getMessage(), e);
            try { out.writeObject(new BidHistoryResponse(false, "Lỗi: " + e.getMessage(), null)); out.flush(); } catch (Exception ignored) {}
        }
    }

    private void handleRegisterAutoBid(ObjectInputStream in, ObjectOutputStream out) {
        try {
            AutoBidConfig config = (AutoBidConfig) in.readObject();
            auctionManager.getAuctionService(); // ensure initialized
            // access autoBidEngine via reflection on auctionService - use AuctionManager
            com.auction.server.service.AutoBidEngine engine = getAutoBidEngine();
            engine.registerAutoBid(config);
            out.writeObject(new SimpleResponse(true, "Đã đăng ký auto-bid thành công."));
            out.flush();
        } catch (Exception e) {
            log.error("Lỗi AUTOBID_REGISTER: {}", e.getMessage(), e);
            try { out.writeObject(new SimpleResponse(false, "Lỗi: " + e.getMessage())); out.flush(); } catch (Exception ignored) {}
        }
    }

    private void handleCancelAutoBid(ObjectInputStream in, ObjectOutputStream out) {
        try {
            int bidderId = in.readInt();
            int auctionId = in.readInt();
            getAutoBidEngine().cancelAutoBid(bidderId, auctionId);
            out.writeObject(new SimpleResponse(true, "Đã hủy auto-bid."));
            out.flush();
        } catch (Exception e) {
            try { out.writeObject(new SimpleResponse(false, "Lỗi: " + e.getMessage())); out.flush(); } catch (Exception ignored) {}
        }
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private com.auction.server.service.AutoBidEngine getAutoBidEngine() {
        // AuctionManager giữ AutoBidEngine private; tạo instance mới chia sẻ BidDAO
        // Trong thực tế nên expose qua AuctionManager. Đây là workaround đơn giản.
        return new com.auction.server.service.AutoBidEngine(bidDAO);
    }

    private AuctionDTO mapToDTO(Auction a, Item item, String sellerName) {
        AuctionDTO dto = new AuctionDTO();
        dto.setAuctionId(a.getId());
        dto.setItemId(a.getItemId());
        if (item != null) {
            dto.setItemName(item.getName());
            dto.setItemDescription(item.getDescription());
            dto.setItemCategory(item.getCategory() != null ? item.getCategory().name() : "");
        }
        dto.setSellerName(sellerName != null ? sellerName : "");
        dto.setStartingPrice(a.getStartingPrice());
        dto.setCurrentPrice(a.getCurrentPrice() != null ? a.getCurrentPrice() : a.getStartingPrice());
        dto.setReservePrice(a.getReservePrice());
        dto.setHighestBidderId(a.getHighestBidderId());
        dto.setStartTime(a.getStartTime());
        dto.setEndTime(a.getEndTime());
        dto.setStatus(a.getStatus() != null ? a.getStatus().name() : "");
        dto.setExtensionCount(a.getExtensionCount());
        dto.setTotalBids(bidDAO.countByAuction(a.getId()));
        return dto;
    }

    private Item createItemByCategory(String category) {
        return switch (category.toUpperCase()) {
            case "ELECTRONICS" -> new Electronics();
            case "ART"         -> new Art();
            case "VEHICLE"     -> new Vehicle();
            default            -> throw new IllegalArgumentException("Danh mục không hợp lệ: " + category);
        };
    }
}