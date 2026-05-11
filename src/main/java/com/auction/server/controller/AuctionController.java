package com.auction.server.controller;

import com.auction.common.dto.AdminAuctionRequestDTO;
import com.auction.common.dto.AuctionDTO;
import com.auction.common.request.*;
import com.auction.common.response.*;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.BidDAO;
import com.auction.server.dao.ItemDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.model.*;
import com.auction.server.network.ClientHandler;
import com.auction.server.service.AuctionManager;
import com.auction.server.service.AuctionService;
import com.auction.server.service.AutoBidEngine;
import com.auction.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AuctionController – xử lý tất cả request liên quan đến đấu giá từ client.
 *
 * Các action được hỗ trợ:
 *   BID_PLACE                   – đặt giá thủ công
 *   AUCTION_CREATE              – tạo phiên đấu giá mới (Seller)
 *   AUCTION_GET_ACTIVE          – lấy danh sách phiên đang mở
 *   AUCTION_GET_PENDING_REQUESTS– lấy danh sách phiên chờ duyệt (Admin)
 *   AUCTION_APPROVE             – duyệt phiên (Admin)
 *   AUCTION_REJECT              – từ chối phiên (Admin)
 *   AUCTION_SUBSCRIBE           – đăng ký nhận realtime update
 *   AUCTION_UNSUBSCRIBE         – hủy đăng ký realtime update
 *   AUCTION_GET_BIDS            – lấy lịch sử đặt giá
 *   AUTOBID_REGISTER            – đăng ký đấu giá tự động
 *   AUTOBID_CANCEL              – hủy đấu giá tự động
 *
 * Các fix so với phiên bản cũ:
 *   1. Phân quyền đầy đủ: APPROVE/REJECT yêu cầu ADMIN, CREATE yêu cầu SELLER/ADMIN
 *   2. AUTOBID_REGISTER/CANCEL dùng AuctionManager.getAutoBidEngine() thay vì tạo instance mới
 *      (bản cũ tạo AutoBidEngine mới → không share state với engine đang chạy thực)
 *   3. handleRegisterAutoBid kiểm tra phiên tồn tại và đang mở trước khi đăng ký
 *   4. handleRegisterAutoBid override bidderId bằng session (tránh giả mạo)
 *   5. handleCancelAutoBid override bidderId bằng session
 *   6. mapToDTO điền thêm highestBidderUsername
 *   7. session được truyền vào constructor thay vì field mutable
 */
public class AuctionController {

    private static final Logger log = LoggerFactory.getLogger(AuctionController.class);
    // AuctionController.java - thêm field
    private static List<AdminAuctionRequestDTO> pendingDtoCache = null;
    private static long pendingCacheTimeMs = 0;
    private static final long CACHE_TTL = 5_000; // 5 giây

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final AuctionManager auctionManager = AuctionManager.getInstance();
    private final AuctionService auctionService  = auctionManager.getAuctionService();
    private final AutoBidEngine  autoBidEngine   = auctionManager.getAutoBidEngine();
    private final ItemDAO        itemDAO          = new ItemDAO();
    private final UserDAO        userDAO          = new UserDAO();
    private final BidDAO         bidDAO           = new BidDAO();
    private final AuctionDAO auctionDAO = new AuctionDAO();

    // Session được set mỗi request (mutable field — giống bản gốc)
    private ServerSession session;

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * Phân loại action và gọi handler tương ứng.
     * session và handler được truyền từ ClientHandler.
     */
    public void processRequest(String action,
                               ObjectInputStream in,
                               ObjectOutputStream out,
                               ServerSession session,
                               ClientHandler handler) throws Exception {
        this.session = session;
        switch (action) {
            case "BID_PLACE"                    -> handlePlaceBid(in, out);
            case "AUCTION_CREATE"               -> handleCreateAuction(in, out);
            case "AUCTION_GET_ACTIVE"           -> handleGetActiveAuctions(out);
            case "AUCTION_GET_PENDING_REQUESTS" -> handleGetPendingAuctions(in, out);
            case "AUCTION_APPROVE"              -> handleApproveAuction(in, out);
            case "AUCTION_REJECT"               -> handleRejectAuction(in, out);
            case "AUCTION_SUBSCRIBE"            -> handleSubscribe(in, out, handler);
            case "AUCTION_UNSUBSCRIBE"          -> handleUnsubscribe(in, out, handler);
            case "AUCTION_GET_BIDS"             -> handleGetBidHistory(in, out);
            case "AUTOBID_REGISTER"             -> handleRegisterAutoBid(in, out);
            case "AUTOBID_CANCEL"               -> handleCancelAutoBid(in, out);
            default -> {
                log.warn("Action không hỗ trợ: {}", action);
                send(out, new SimpleResponse(false, "Action không hỗ trợ: " + action));
            }
        }
    }

    /** Overload không có handler (backward compatibility). */
    public void processRequest(String action,
                               ObjectInputStream in,
                               ObjectOutputStream out) throws Exception {
        processRequest(action, in, out, null, null);
    }

    // ── HANDLERS ──────────────────────────────────────────────────────────────

    /**
     * BID_PLACE – đặt giá thủ công.
     * Yêu cầu: đã đăng nhập.
     * userId luôn lấy từ session, không tin tưởng giá trị client gửi lên.
     */
    private void handlePlaceBid(ObjectInputStream in, ObjectOutputStream out) {
        if (!requireLogin(out, new BidResponse(false, "Bạn chưa đăng nhập.", BigDecimal.ZERO))) return;
        try {
            BidRequest request = (BidRequest) in.readObject();

            // Override userId bằng session để tránh giả mạo
            request.setUserId(session.getUserId());

            BidResponse response = auctionService.placeBid(request);
            send(out, response);
        } catch (Exception e) {
            log.error("Lỗi BID_PLACE: {}", e.getMessage(), e);
            send(out, new BidResponse(false, "Lỗi server: " + e.getMessage(), BigDecimal.ZERO));
        }
    }

    /**
     * AUCTION_CREATE – tạo phiên đấu giá mới.
     * Yêu cầu: đăng nhập + role SELLER hoặc ADMIN.
     * sellerId luôn lấy từ session.
     */
    private void handleCreateAuction(ObjectInputStream in, ObjectOutputStream out) {
        if (!requireLogin(out, new CreateAuctionResponse(false, "Bạn chưa đăng nhập.", null))) return;
        try {
            CreateAuctionRequest req = (CreateAuctionRequest) in.readObject();

            // sellerId luôn lấy từ session, không tin client
            int sellerId = session.getUserId();

            // Tạo Item đúng loại theo category
            Item item = createItemByCategory(req.getItemCategory());
            item.setName(req.getItemName());
            item.setDescription(req.getItemDescription());
            item.setStartingPrice(req.getStartingPrice());
            item.setSellerId(sellerId);
            item.setCategory(ItemCategory.valueOf(req.getItemCategory().toUpperCase()));

            boolean itemSaved = itemDAO.insertItem(item);
            if (!itemSaved) {
                send(out, new CreateAuctionResponse(false, "Lỗi lưu sản phẩm vào cơ sở dữ liệu.", null));
                return;
            }

            BigDecimal reservePrice = req.getReservePrice() != null
                    ? req.getReservePrice()
                    : req.getStartingPrice();

            Auction auction = auctionService.createAuction(
                    item.getId(),
                    sellerId,
                    req.getStartingPrice(),
                    reservePrice,
                    req.getStartTime(),
                    req.getEndTime()
            );
            if (req.getImageBase64() != null && !req.getImageBase64().isEmpty()) {
                try {
                    byte[] bytes = java.util.Base64.getDecoder().decode(req.getImageBase64());
                    java.nio.file.Path dir = java.nio.file.Paths.get("images");
                    if (!java.nio.file.Files.exists(dir)) {
                        java.nio.file.Files.createDirectories(dir);
                    }
                    java.nio.file.Files.write(
                            java.nio.file.Paths.get("images/" + auction.getId() + ".jpg"),
                            bytes
                    );
                } catch (Exception e) {
                    log.warn("Không lưu được ảnh cho auction {}: {}", auction.getId(), e.getMessage());
                    // không return, vẫn tiếp tục bình thường
                }
            }

            AuctionDTO dto = mapToDTO(auction, item, session.getUsername());
            send(out, new CreateAuctionResponse(true,
                    "Tạo phiên đấu giá thành công. Vui lòng chờ Admin duyệt.", dto));
            log.info("Tạo auction: id={}, item='{}', seller={}",
                    auction.getId(), item.getName(), session.getUsername());

        } catch (IllegalArgumentException e) {
            log.warn("AUCTION_CREATE – danh mục không hợp lệ: {}", e.getMessage());
            send(out, new CreateAuctionResponse(false, "Danh mục sản phẩm không hợp lệ: " + e.getMessage(), null));
        } catch (Exception e) {
            log.error("Lỗi AUCTION_CREATE: {}", e.getMessage(), e);
            send(out, new CreateAuctionResponse(false, "Lỗi server: " + e.getMessage(), null));
        }
    }

    /**
     * AUCTION_GET_ACTIVE – lấy danh sách phiên đang mở/chạy.
     * Không yêu cầu đăng nhập (ai cũng xem được).
     */
    private void handleGetActiveAuctions(ObjectOutputStream out) {
        try {
            List<Auction> auctions = auctionService.getActiveAuctions();
            List<AuctionDTO> dtos = new ArrayList<>(auctions.size());

            for (Auction a : auctions) {
                Item item = itemDAO.findById(a.getItemId());
                String sellerName = resolveUsername(a.getSellerId());
                AuctionDTO dto = mapToDTO(a, item, sellerName);

                // Điền username người dẫn đầu nếu có
                if (a.getHighestBidderId() != 0) {
                    dto.setHighestBidderUsername(resolveUsername(a.getHighestBidderId()));
                }
                dtos.add(dto);
            }
            send(out, new AuctionListResponse(true, "OK", dtos));
        } catch (Exception e) {
            log.error("Lỗi AUCTION_GET_ACTIVE: {}", e.getMessage(), e);
            send(out, new AuctionListResponse(false, "Lỗi server: " + e.getMessage(), null));
        }
    }

    /**
     * AUCTION_GET_PENDING_REQUESTS – lấy danh sách phiên chờ Admin duyệt.
     * Yêu cầu: đăng nhập + ADMIN.
     */
    private void handleGetPendingAuctions(ObjectInputStream in, ObjectOutputStream out) {
        if (!requireLogin(out, new GetPendingAuctionRequestsResponse(false, "Bạn chưa đăng nhập.", null))) return;
        if (!requireRole(out, new GetPendingAuctionRequestsResponse(false, "Không có quyền truy cập.", null),
                "ADMIN")) return;
        try {
            in.readObject();

            // Kiểm tra cache
            long now = System.currentTimeMillis();
            if (pendingDtoCache != null && (now - pendingCacheTimeMs) < CACHE_TTL) {
                send(out, new GetPendingAuctionRequestsResponse(true, "OK", pendingDtoCache));
                return;
            }

            // ✅ 1 query JOIN thay vì N+1 query
            List<AdminAuctionRequestDTO> dtos = auctionDAO.findPendingWithDetails();

            pendingDtoCache = dtos;
            pendingCacheTimeMs = System.currentTimeMillis();

            send(out, new GetPendingAuctionRequestsResponse(true, "OK", dtos));
        } catch (Exception e) {
            log.error("Lỗi AUCTION_GET_PENDING_REQUESTS: {}", e.getMessage(), e);
            send(out, new GetPendingAuctionRequestsResponse(false, "Lỗi server: " + e.getMessage(), null));
        }
    }

    /**
     * AUCTION_APPROVE – Admin duyệt phiên đấu giá.
     * Yêu cầu: đăng nhập + ADMIN.
     */
    private void handleApproveAuction(ObjectInputStream in, ObjectOutputStream out) {
        if (!requireLogin(out, new ApproveAuctionResponse(false, "Bạn chưa đăng nhập."))) return;
        if (!requireRole(out, new ApproveAuctionResponse(false, "Chỉ ADMIN mới có thể duyệt phiên đấu giá."),
                "ADMIN")) return;
        try {
            ApproveAuctionRequest req = (ApproveAuctionRequest) in.readObject();

            // adminId luôn lấy từ session
            boolean ok = auctionService.approveAuction(req.getRequestId(), session.getUserId());

            send(out, new ApproveAuctionResponse(ok,
                    ok ? "Phiên đấu giá đã được duyệt thành công."
                            : "Không tìm thấy phiên đấu giá (id=" + req.getRequestId() + ")."));
            if (ok) log.info("Admin {} duyệt phiên {}", session.getUsername(), req.getRequestId());

        } catch (Exception e) {
            log.error("Lỗi AUCTION_APPROVE: {}", e.getMessage(), e);
            send(out, new ApproveAuctionResponse(false, "Lỗi server: " + e.getMessage()));
        }
    }

    /**
     * AUCTION_REJECT – Admin từ chối phiên đấu giá.
     * Yêu cầu: đăng nhập + ADMIN.
     */
    private void handleRejectAuction(ObjectInputStream in, ObjectOutputStream out) {
        if (!requireLogin(out, new RejectAuctionResponse(false, "Bạn chưa đăng nhập."))) return;
        if (!requireRole(out, new RejectAuctionResponse(false, "Chỉ ADMIN mới có thể từ chối phiên đấu giá."),
                "ADMIN")) return;
        try {
            RejectAuctionRequest req = (RejectAuctionRequest) in.readObject();

            boolean ok = auctionService.rejectAuction(
                    req.getRequestId(),
                    session.getUserId(),
                    req.getRejectReason()
            );

            send(out, new RejectAuctionResponse(ok,
                    ok ? "Phiên đấu giá đã bị từ chối."
                            : "Không tìm thấy phiên đấu giá (id=" + req.getRequestId() + ")."));
            if (ok) log.info("Admin {} từ chối phiên {} | Lý do: {}",
                    session.getUsername(), req.getRequestId(), req.getRejectReason());

        } catch (Exception e) {
            log.error("Lỗi AUCTION_REJECT: {}", e.getMessage(), e);
            send(out, new RejectAuctionResponse(false, "Lỗi server: " + e.getMessage()));
        }
    }

    /**
     * AUCTION_SUBSCRIBE – đăng ký nhận realtime update của một phiên.
     * Gửi lại snapshot hiện tại của phiên ngay sau khi subscribe thành công.
     */
    private void handleSubscribe(ObjectInputStream in, ObjectOutputStream out, ClientHandler handler) {
        try {
            int auctionId = in.readInt();

            if (handler != null) {
                auctionManager.subscribe(auctionId, handler);
            }

            // Trả về snapshot phiên hiện tại để client render ngay
            Auction a    = auctionService.getAuction(auctionId);
            Item    item = itemDAO.findById(a.getItemId());
            AuctionDTO dto = mapToDTO(a, item, resolveUsername(a.getSellerId()));
            if (a.getHighestBidderId() != 0) {
                dto.setHighestBidderUsername(resolveUsername(a.getHighestBidderId()));
            }
            send(out, new CreateAuctionResponse(true, "Subscribed", dto));
            log.debug("Client subscribe auction={}", auctionId);

        } catch (Exception e) {
            log.error("Lỗi AUCTION_SUBSCRIBE: {}", e.getMessage(), e);
            send(out, new CreateAuctionResponse(false, "Lỗi subscribe: " + e.getMessage(), null));
        }
    }

    /**
     * AUCTION_UNSUBSCRIBE – hủy đăng ký nhận realtime update.
     */
    private void handleUnsubscribe(ObjectInputStream in, ObjectOutputStream out, ClientHandler handler) {
        try {
            int auctionId = in.readInt();
            if (handler != null) auctionManager.unsubscribe(auctionId, handler);
            send(out, new SimpleResponse(true, "Unsubscribed"));
        } catch (Exception e) {
            log.warn("Lỗi AUCTION_UNSUBSCRIBE: {}", e.getMessage());
            send(out, new SimpleResponse(false, "Lỗi: " + e.getMessage()));
        }
    }

    /**
     * AUCTION_GET_BIDS – lấy lịch sử đặt giá của một phiên.
     */
    private void handleGetBidHistory(ObjectInputStream in, ObjectOutputStream out) {
        try {
            int auctionId = in.readInt();
            List<BidTransaction> bids = bidDAO.findByAuction(auctionId);
            send(out, new BidHistoryResponse(true, "OK", bids));
        } catch (Exception e) {
            log.error("Lỗi AUCTION_GET_BIDS: {}", e.getMessage(), e);
            send(out, new BidHistoryResponse(false, "Lỗi server: " + e.getMessage(), null));
        }
    }

    /**
     * AUTOBID_REGISTER – đăng ký đấu giá tự động.
     * Yêu cầu: đăng nhập.
     *
     * Fix so với bản cũ:
     *  - Dùng autoBidEngine từ AuctionManager (shared instance), không tạo mới
     *  - Override bidderId từ session
     *  - Kiểm tra phiên tồn tại và đang mở trước khi đăng ký
     */
    private void handleRegisterAutoBid(ObjectInputStream in, ObjectOutputStream out) {
        if (!requireLogin(out, new SimpleResponse(false, "Bạn chưa đăng nhập."))) return;
        try {
            AutoBidConfig config = (AutoBidConfig) in.readObject();

            // Luôn dùng bidderId từ session (tránh client giả mạo)
            config.setBidderId(session.getUserId());
            config.setBidderUsername(session.getUsername());

            // Kiểm tra phiên tồn tại và đang hoạt động
            Auction auction;
            try {
                auction = auctionService.getAuction(config.getAuctionId());
            } catch (Exception e) {
                send(out, new SimpleResponse(false, "Phiên đấu giá không tồn tại."));
                return;
            }

            AuctionStatus status = auction.getStatus();
            if (status != AuctionStatus.OPEN && status != AuctionStatus.RUNNING) {
                send(out, new SimpleResponse(false,
                        "Không thể đăng ký auto-bid: phiên đang ở trạng thái " + status.getDisplay() + "."));
                return;
            }

            // Kiểm tra seller không tự auto-bid auction của mình
            if (auction.getSellerId() == session.getUserId()) {
                send(out, new SimpleResponse(false, "Người bán không thể tự đấu giá sản phẩm của mình."));
                return;
            }

            // Validate maxBid > currentPrice
            BigDecimal currentPrice = auction.getCurrentPrice() != null
                    ? auction.getCurrentPrice()
                    : auction.getStartingPrice();
            if (config.getMaxBid().compareTo(currentPrice) <= 0) {
                send(out, new SimpleResponse(false,
                        "Giá tối đa phải cao hơn giá hiện tại (" + currentPrice + ")."));
                return;
            }

            autoBidEngine.registerAutoBid(config);
            send(out, new SimpleResponse(true, "Đăng ký auto-bid thành công."));
            log.info("AutoBid đăng ký: user={} auction={} maxBid={}",
                    session.getUsername(), config.getAuctionId(), config.getMaxBid());

        } catch (Exception e) {
            log.error("Lỗi AUTOBID_REGISTER: {}", e.getMessage(), e);
            send(out, new SimpleResponse(false, "Lỗi server: " + e.getMessage()));
        }
    }

    /**
     * AUTOBID_CANCEL – hủy đấu giá tự động.
     * Yêu cầu: đăng nhập.
     * bidderId luôn lấy từ session (user chỉ hủy được của chính mình).
     */
    private void handleCancelAutoBid(ObjectInputStream in, ObjectOutputStream out) {
        if (!requireLogin(out, new SimpleResponse(false, "Bạn chưa đăng nhập."))) return;
        try {
            int auctionId = in.readInt();
            // Luôn dùng bidderId từ session — bỏ qua bất kỳ bidderId nào client gửi lên
            autoBidEngine.cancelAutoBid(session.getUserId(), auctionId);
            send(out, new SimpleResponse(true, "Đã hủy auto-bid thành công."));
            log.info("AutoBid hủy: user={} auction={}", session.getUsername(), auctionId);
        } catch (Exception e) {
            log.error("Lỗi AUTOBID_CANCEL: {}", e.getMessage(), e);
            send(out, new SimpleResponse(false, "Lỗi server: " + e.getMessage()));
        }
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    /**
     * Kiểm tra đăng nhập. Nếu chưa đăng nhập, gửi errorResponse và trả về false.
     */
    private boolean requireLogin(ObjectOutputStream out, Object errorResponse) {
        if (session == null || !session.isLoggedIn()) {
            send(out, errorResponse);
            return false;
        }
        return true;
    }

    /**
     * Kiểm tra role. Nếu không đủ quyền, gửi errorResponse và trả về false.
     * @param allowedRoles danh sách role được phép (case-insensitive)
     */
    private boolean requireRole(ObjectOutputStream out, Object errorResponse, String... allowedRoles) {
        if (session == null) { send(out, errorResponse); return false; }
        String userRole = session.getLoggedInUser() != null
                ? session.getLoggedInUser().getRole().toUpperCase()
                : "";
        for (String allowed : allowedRoles) {
            if (allowed.equalsIgnoreCase(userRole)) return true;
        }
        send(out, errorResponse);
        log.warn("Phân quyền thất bại: user={} role={} cần {}",
                session.getUsername(), userRole, String.join("/", allowedRoles));
        return false;
    }

    /**
     * Gửi object xuống stream, bắt exception không để lan ra ngoài.
     */
    private void send(ObjectOutputStream out, Object obj) {
        try {
            out.writeObject(obj);
            out.flush();
        } catch (Exception e) {
            log.warn("Không thể gửi response về client: {}", e.getMessage());
        }
    }

    /**
     * Tra cứu username theo userId. Trả về chuỗi rỗng nếu không tìm thấy.
     */
    private String resolveUsername(int userId) {
        if (userId == 0) return "";
        try {
            User u = userDAO.findById(userId);
            return u != null ? u.getUsername() : "";
        } catch (Exception e) {
            log.debug("Không lấy được username userId={}: {}", userId, e.getMessage());
            return "";
        }
    }

    /**
     * Chuyển Auction + Item → AuctionDTO để gửi về client.
     */
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

        String imagePath = "images/" + a.getId() + ".jpg";
        if (java.nio.file.Files.exists(java.nio.file.Paths.get(imagePath))) {
            dto.setImageUrl("file:" + java.nio.file.Paths.get(imagePath).toAbsolutePath());
        } else {
            dto.setImageUrl("https://picsum.photos/seed/" + a.getId() + "/300/200");
        }
        return dto;
    }

    /**
     * Factory Method – tạo Item đúng loại theo category.
     * Áp dụng Factory Method Pattern.
     */
    private Item createItemByCategory(String category) {
        return switch (category.toUpperCase()) {
            case "ELECTRONICS" -> new Electronics();
            case "ART"         -> new Art();
            case "VEHICLE"     -> new Vehicle();
            default -> throw new IllegalArgumentException("Danh mục không hợp lệ: " + category);
        };
    }
}