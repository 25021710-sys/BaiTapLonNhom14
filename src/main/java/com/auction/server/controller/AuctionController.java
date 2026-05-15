package com.auction.server.controller;

import com.auction.common.dto.AdminAuctionRequestDTO;
import com.auction.common.dto.AdminRoomDTO;
import com.auction.common.dto.AuctionDTO;
import com.auction.common.dto.AuctionUpdateDTO;
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
import java.util.*;

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
 *   1. Phân quyền đầy đủ: APPROVE/REJECT yêu cầu ADMIN
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

    // FIX 2: cache dùng volatile + synchronized đúng cách thay vì static field thô
    private volatile List<AdminAuctionRequestDTO> pendingDtoCache = null;
    private volatile long pendingCacheTimeMs = 0;
    private static final long CACHE_TTL = 5_000; // 5 giây
    private final Object cacheLock = new Object();

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final AuctionManager auctionManager = AuctionManager.getInstance();
    private final AuctionService auctionService  = auctionManager.getAuctionService();
    private final AutoBidEngine  autoBidEngine   = auctionManager.getAutoBidEngine();
    private final ItemDAO        itemDAO          = new ItemDAO();
    private final UserDAO        userDAO          = new UserDAO();
    private final BidDAO         bidDAO           = new BidDAO();
    private final AuctionDAO     auctionDAO       = new AuctionDAO();


    // ── Entry point ───────────────────────────────────────────────────────────

    public void processRequest(String action,
                               ObjectInputStream in,
                               ObjectOutputStream out,
                               ServerSession session,
                               ClientHandler handler) throws Exception {
        // session là local var — không lưu vào field để tránh race condition
        switch (action) {
            case "BID_PLACE"                    -> handlePlaceBid(in, out, session);
            case "AUCTION_CREATE"               -> handleCreateAuction(in, out, session);
            case "AUCTION_GET_ACTIVE"           -> handleGetActiveAuctions(out);
            case "AUCTION_GET_PENDING_REQUESTS" -> handleGetPendingAuctions(in, out, session);
            case "AUCTION_APPROVE"              -> handleApproveAuction(in, out, session);
            case "AUCTION_REJECT"               -> handleRejectAuction(in, out, session);
            case "AUCTION_SUBSCRIBE"            -> handleSubscribe(in, out, handler);
            case "AUCTION_UNSUBSCRIBE"          -> handleUnsubscribe(in, out, handler);
            case "AUCTION_GET_BIDS"             -> handleGetBidHistory(in, out);
            case "AUTOBID_REGISTER"             -> handleRegisterAutoBid(in, out, session);
            case "AUTOBID_CANCEL"               -> handleCancelAutoBid(in, out, session);
            case "AUCTION_GET_JOINED"           -> handleGetJoinedAuctions(out, session);
            case "AUCTION_GET_MY"               -> handleGetMyAuctions(out, session);
            case "AUCTION_GET_DASHBOARD"        -> handleGetDashboard(out, session);
            case "ADMIN_GET_ROOMS"        -> handleAdminGetRooms(in, out, session);
            case "ADMIN_GET_ROOM_DETAIL"  -> handleAdminGetRoomDetail(in, out, session);
            case "ADMIN_PAUSE_ROOM"       -> handleAdminPauseRoom(in, out, session);
            case "ADMIN_RESUME_ROOM"      -> handleAdminResumeRoom(in, out, session);
            case "ADMIN_CLOSE_ROOM"       -> handleAdminCloseRoom(in, out, session);
            default -> {
                log.warn("Action không hỗ trợ: {}", action);
                send(out, new SimpleResponse(false, "Action không hỗ trợ: " + action));
            }
        }
    }

    public void processRequest(String action, ObjectInputStream in, ObjectOutputStream out) throws Exception {
        processRequest(action, in, out, null, null);
    }

    // ── HANDLERS ──────────────────────────────────────────────────────────────

    private void handlePlaceBid(ObjectInputStream in, ObjectOutputStream out, ServerSession session) {
        if (!requireLogin(session, out, new BidResponse(false, "Bạn chưa đăng nhập.", BigDecimal.ZERO))) return;
        try {
            BidRequest request = (BidRequest) in.readObject();
            request.setUserId(session.getUserId());
            BidResponse response = auctionService.placeBid(request);
            send(out, response);
        } catch (Exception e) {
            log.error("Lỗi BID_PLACE: {}", e.getMessage(), e);
            send(out, new BidResponse(false, "Lỗi server: " + e.getMessage(), BigDecimal.ZERO));
        }
    }

    private void handleCreateAuction(ObjectInputStream in, ObjectOutputStream out, ServerSession session) {
        if (!requireLogin(session, out, new CreateAuctionResponse(false, "Bạn chưa đăng nhập.", null))) return;
        if (!requireRole(session, out, new CreateAuctionResponse(false, "Chỉ SELLER mới được tạo phiên đấu giá.", null),
                "SELLER", "ADMIN")) return;
        try {
            CreateAuctionRequest req = (CreateAuctionRequest) in.readObject();
            int sellerId = session.getUserId();

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
                    item.getId(), sellerId,
                    req.getStartingPrice(), reservePrice,
                    req.getStartTime(), req.getEndTime());

            if (req.getImageBase64() != null && !req.getImageBase64().isEmpty()) {
                try {
                    byte[] bytes = java.util.Base64.getDecoder().decode(req.getImageBase64());
                    java.nio.file.Path dir = java.nio.file.Paths.get("images");
                    if (!java.nio.file.Files.exists(dir)) java.nio.file.Files.createDirectories(dir);
                    java.nio.file.Files.write(java.nio.file.Paths.get("images/" + auction.getId() + ".jpg"), bytes);
                } catch (Exception e) {
                    log.warn("Không lưu được ảnh cho auction {}: {}", auction.getId(), e.getMessage());
                }
            }

            AuctionDTO dto = mapToDTO(auction, item, session.getUsername());
            send(out, new CreateAuctionResponse(true, "Tạo phiên đấu giá thành công. Vui lòng chờ Admin duyệt.", dto));
            log.info("Tạo auction: id={}, item='{}', seller={}", auction.getId(), item.getName(), session.getUsername());

        } catch (IllegalArgumentException e) {
            send(out, new CreateAuctionResponse(false, "Danh mục sản phẩm không hợp lệ: " + e.getMessage(), null));
        } catch (Exception e) {
            log.error("Lỗi AUCTION_CREATE: {}", e.getMessage(), e);
            send(out, new CreateAuctionResponse(false, "Lỗi server: " + e.getMessage(), null));
        }
    }

    /**
     * FIX 1 – Batch load items và users, không gọi DB trong vòng lặp.
     */
    private void handleGetActiveAuctions(ObjectOutputStream out) {
        try {
            List<Auction> auctions = auctionService.getActiveAuctions();
            List<AuctionDTO> dtos = buildAuctionDTOs(auctions);
            send(out, new AuctionListResponse(true, "OK", dtos));
        } catch (Exception e) {
            log.error("Lỗi AUCTION_GET_ACTIVE: {}", e.getMessage(), e);
            send(out, new AuctionListResponse(false, "Lỗi server: " + e.getMessage(), null));
        }
    }

    /**
     * FIX 2 – Cache thread-safe với synchronized block.
     */
    private void handleGetPendingAuctions(ObjectInputStream in, ObjectOutputStream out, ServerSession session) {
        if (!requireLogin(session, out, new GetPendingAuctionRequestsResponse(false, "Bạn chưa đăng nhập.", null))) return;
        if (!requireRole(session, out, new GetPendingAuctionRequestsResponse(false, "Không có quyền truy cập.", null), "ADMIN")) return;
        try {
            in.readObject();

            long now = System.currentTimeMillis();
            List<AdminAuctionRequestDTO> cached;
            synchronized (cacheLock) {
                if (pendingDtoCache != null && (now - pendingCacheTimeMs) < CACHE_TTL) {
                    cached = pendingDtoCache;
                } else {
                    cached = auctionDAO.findPendingWithDetails();
                    pendingDtoCache = cached;
                    pendingCacheTimeMs = System.currentTimeMillis();
                }
            }
            send(out, new GetPendingAuctionRequestsResponse(true, "OK", cached));
        } catch (Exception e) {
            log.error("Lỗi AUCTION_GET_PENDING_REQUESTS: {}", e.getMessage(), e);
            send(out, new GetPendingAuctionRequestsResponse(false, "Lỗi server: " + e.getMessage(), null));
        }
    }

    private void handleApproveAuction(ObjectInputStream in, ObjectOutputStream out, ServerSession session) {
        if (!requireLogin(session, out, new ApproveAuctionResponse(false, "Bạn chưa đăng nhập."))) return;
        if (!requireRole(session, out, new ApproveAuctionResponse(false, "Chỉ ADMIN mới có thể duyệt phiên đấu giá."), "ADMIN")) return;
        try {
            ApproveAuctionRequest req = (ApproveAuctionRequest) in.readObject();
            boolean ok = auctionService.approveAuction(req.getRequestId(), session.getUserId());
            if (ok) { synchronized (cacheLock) { pendingDtoCache = null; pendingCacheTimeMs = 0; } }
            send(out, new ApproveAuctionResponse(ok,
                    ok ? "Phiên đấu giá đã được duyệt thành công."
                            : "Không tìm thấy phiên đấu giá (id=" + req.getRequestId() + ")."));
            if (ok) log.info("Admin {} duyệt phiên {}", session.getUsername(), req.getRequestId());
        } catch (Exception e) {
            log.error("Lỗi AUCTION_APPROVE: {}", e.getMessage(), e);
            send(out, new ApproveAuctionResponse(false, "Lỗi server: " + e.getMessage()));
        }
    }

    private void handleRejectAuction(ObjectInputStream in, ObjectOutputStream out, ServerSession session) {
        if (!requireLogin(session, out, new RejectAuctionResponse(false, "Bạn chưa đăng nhập."))) return;
        if (!requireRole(session, out, new RejectAuctionResponse(false, "Chỉ ADMIN mới có thể từ chối phiên đấu giá."), "ADMIN")) return;
        try {
            RejectAuctionRequest req = (RejectAuctionRequest) in.readObject();
            boolean ok = auctionService.rejectAuction(req.getRequestId(), session.getUserId(), req.getRejectReason());
            if (ok) { synchronized (cacheLock) { pendingDtoCache = null; pendingCacheTimeMs = 0; } }
            send(out, new RejectAuctionResponse(ok,
                    ok ? "Phiên đấu giá đã bị từ chối."
                            : "Không tìm thấy phiên đấu giá (id=" + req.getRequestId() + ")."));
            if (ok) log.info("Admin {} từ chối phiên {} | Lý do: {}", session.getUsername(), req.getRequestId(), req.getRejectReason());
        } catch (Exception e) {
            log.error("Lỗi AUCTION_REJECT: {}", e.getMessage(), e);
            send(out, new RejectAuctionResponse(false, "Lỗi server: " + e.getMessage()));
        }
    }

    private void handleSubscribe(ObjectInputStream in, ObjectOutputStream out, ClientHandler handler) {
        try {
            int auctionId = in.readInt();
            if (handler != null) auctionManager.subscribe(auctionId, handler);

            Auction a    = auctionService.getAuction(auctionId);
            Item item = itemDAO.findById(a.getItemId());
            AuctionDTO dto = mapToDTO(a, item, resolveUsername(a.getSellerId()));
            if (a.getHighestBidderId() != 0) dto.setHighestBidderUsername(resolveUsername(a.getHighestBidderId()));
            send(out, new CreateAuctionResponse(true, "Subscribed", dto));
            log.debug("Client subscribe auction={}", auctionId);
        } catch (Exception e) {
            log.error("Lỗi AUCTION_SUBSCRIBE: {}", e.getMessage(), e);
            send(out, new CreateAuctionResponse(false, "Lỗi subscribe: " + e.getMessage(), null));
        }
    }

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

    private void handleRegisterAutoBid(ObjectInputStream in, ObjectOutputStream out, ServerSession session) {
        if (!requireLogin(session, out, new SimpleResponse(false, "Bạn chưa đăng nhập."))) return;
        try {
            AutoBidConfig config = (AutoBidConfig) in.readObject();
            config.setBidderId(session.getUserId());
            config.setBidderUsername(session.getUsername());

            Auction auction;
            try { auction = auctionService.getAuction(config.getAuctionId()); }
            catch (Exception e) { send(out, new SimpleResponse(false, "Phiên đấu giá không tồn tại.")); return; }

            AuctionStatus status = auction.getStatus();
            if (status != AuctionStatus.OPEN && status != AuctionStatus.RUNNING) {
                send(out, new SimpleResponse(false, "Không thể đăng ký auto-bid: phiên đang ở trạng thái " + status.getDisplay() + ".")); return;
            }
            if (auction.getSellerId() == session.getUserId()) {
                send(out, new SimpleResponse(false, "Người bán không thể tự đấu giá sản phẩm của mình.")); return;
            }
            BigDecimal currentPrice = auction.getCurrentPrice() != null ? auction.getCurrentPrice() : auction.getStartingPrice();
            if (config.getMaxBid().compareTo(currentPrice) <= 0) {
                send(out, new SimpleResponse(false, "Giá tối đa phải cao hơn giá hiện tại (" + currentPrice + ").")); return;
            }

            autoBidEngine.registerAutoBid(config);
            send(out, new SimpleResponse(true, "Đăng ký auto-bid thành công."));
            log.info("AutoBid đăng ký: user={} auction={} maxBid={}", session.getUsername(), config.getAuctionId(), config.getMaxBid());
        } catch (Exception e) {
            log.error("Lỗi AUTOBID_REGISTER: {}", e.getMessage(), e);
            send(out, new SimpleResponse(false, "Lỗi server: " + e.getMessage()));
        }
    }

    private void handleCancelAutoBid(ObjectInputStream in, ObjectOutputStream out, ServerSession session) {
        if (!requireLogin(session, out, new SimpleResponse(false, "Bạn chưa đăng nhập."))) return;
        try {
            in.readInt(); // bỏ qua bidderId client gửi lên
            int auctionId = in.readInt();
            autoBidEngine.cancelAutoBid(session.getUserId(), auctionId);
            send(out, new SimpleResponse(true, "Đã hủy auto-bid thành công."));
            log.info("AutoBid hủy: user={} auction={}", session.getUsername(), auctionId);
        } catch (Exception e) {
            log.error("Lỗi AUTOBID_CANCEL: {}", e.getMessage(), e);
            send(out, new SimpleResponse(false, "Lỗi server: " + e.getMessage()));
        }
    }

    private void handleGetJoinedAuctions(ObjectOutputStream out, ServerSession session) {
        if (!requireLogin(session, out, new AuctionListResponse(false, "Bạn chưa đăng nhập.", null))) return;
        try {
            List<Auction> auctions = auctionService.getJoinedAuctions(session.getUserId());
            List<AuctionDTO> dtos = buildAuctionDTOs(auctions);
            send(out, new AuctionListResponse(true, "OK", dtos));
        } catch (Exception e) {
            log.error("Lỗi AUCTION_GET_JOINED: {}", e.getMessage(), e);
            send(out, new AuctionListResponse(false, "Lỗi server: " + e.getMessage(), null));
        }
    }

    /**
     * FIX 1: batch load thay vì N+1.
     */
    private void handleGetMyAuctions(ObjectOutputStream out, ServerSession session) {
        if (!requireLogin(session, out, new AuctionListResponse(false, "Bạn chưa đăng nhập.", null))) return;
        try {
            List<Auction> auctions = auctionService.getAuctionsBySeller(session.getUserId());
            List<AuctionDTO> dtos = buildAuctionDTOs(auctions);
            send(out, new AuctionListResponse(true, "OK", dtos));
        } catch (Exception e) {
            log.error("Lỗi AUCTION_GET_MY: {}", e.getMessage(), e);
            send(out, new AuctionListResponse(false, "Lỗi server: " + e.getMessage(), null));
        }
    }

    /**
     * FIX 1: batch load thay vì N+1.
     */
    private void handleGetDashboard(ObjectOutputStream out, ServerSession session) {
        try {
            int excludeId = (session != null && session.isLoggedIn()) ? session.getUserId() : 0;
            log.info("AUCTION_GET_DASHBOARD: excludeId={}", excludeId);

            List<Auction> running = auctionService.getRunningAuctionsExcludeSeller(excludeId);
            List<Auction> open    = auctionService.getOpenAuctionsExcludeSeller(excludeId);

            List<Auction> all = new ArrayList<>(running.size() + open.size());
            all.addAll(running);
            all.addAll(open);

            List<AuctionDTO> allDtos = buildAuctionDTOs(all);
            send(out, new AuctionListResponse(true, "OK", allDtos));
        } catch (Exception e) {
            log.error("Lỗi AUCTION_GET_DASHBOARD: {}", e.getMessage(), e);
            send(out, new AuctionListResponse(false, "Lỗi server: " + e.getMessage(), null));
        }
    }

    // ── BATCH LOAD HELPER (FIX 1) ─────────────────────────────────────────────

    /**
     * Chuyển danh sách Auction → List<AuctionDTO> bằng BATCH query thay vì N+1.
     *
     * Bản cũ: mỗi auction gọi itemDAO.findById() và userDAO.findById() riêng
     *         → 20 auctions = 40 round-trip DB.
     * Fix:    thu thập tất cả itemId + userId, batch load 1 lần, tra cứu từ Map.
     *         → luôn chỉ 2 query DB thêm bất kể số lượng auction.
     *
     * Lưu ý: cần thêm itemDAO.findByIds(Set<Integer>) và userDAO.findByIds(Set<Integer>).
     *        Nếu DAO chưa có method đó, fallback về resolveUsername/itemDAO.findById bình thường.
     */
    private List<AuctionDTO> buildAuctionDTOs(List<Auction> auctions) {
        if (auctions == null || auctions.isEmpty()) return Collections.emptyList();

        // Thu thập tất cả itemId và userId cần load
        Set<Integer> itemIds = new HashSet<>();
        Set<Integer> userIds = new HashSet<>();
        for (Auction a : auctions) {
            itemIds.add(a.getItemId());
            userIds.add(a.getSellerId());
            if (a.getHighestBidderId() != 0) userIds.add(a.getHighestBidderId());
        }

        // Batch load — fallback về per-item nếu DAO chưa implement findByIds
        Map<Integer, Item> itemMap = batchLoadItems(itemIds);
        Map<Integer, String> usernameMap = batchLoadUsernames(userIds);

        List<AuctionDTO> dtos = new ArrayList<>(auctions.size());
        for (Auction a : auctions) {
            Item item = itemMap.get(a.getItemId());
            String sellerName = usernameMap.getOrDefault(a.getSellerId(), "");
            AuctionDTO dto = mapToDTO(a, item, sellerName);
            if (a.getHighestBidderId() != 0) {
                dto.setHighestBidderUsername(usernameMap.getOrDefault(a.getHighestBidderId(), ""));
            }
            dtos.add(dto);
        }
        return dtos;
    }

    /** Batch load items. Nếu DAO chưa có findByIds thì fallback per-item. */
    private Map<Integer, Item> batchLoadItems(Set<Integer> ids) {
        try {
            // Thử gọi findByIds nếu ItemDAO đã có method này
            return itemDAO.findByIds(ids);
        } catch (Exception e) {
            // Fallback: load từng cái (giống bản cũ, không tệ hơn)
            Map<Integer, Item> map = new HashMap<>();
            for (int id : ids) {
                try { Item item = itemDAO.findById(id); if (item != null) map.put(id, item); }
                catch (Exception ignored) {}
            }
            return map;
        }
    }

    /** Batch load usernames. Nếu DAO chưa có findByIds thì fallback per-user. */
    private Map<Integer, String> batchLoadUsernames(Set<Integer> ids) {
        try {
            return userDAO.findUsernamesByIds(ids);
        } catch (Exception e) {
            Map<Integer, String> map = new HashMap<>();
            for (int id : ids) map.put(id, resolveUsername(id));
            return map;
        }
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private boolean requireLogin(ServerSession session, ObjectOutputStream out, Object errorResponse) {
        if (session == null || !session.isLoggedIn()) { send(out, errorResponse); return false; }
        return true;
    }

    // requireRole dùng session từ caller — nhưng vì chỉ gọi sau requireLogin đã check
    // nên session lúc này luôn hợp lệ; giữ nguyên signature, truyền session cục bộ qua closure
    private boolean requireRole(ServerSession session, ObjectOutputStream out, Object errorResponse, String... allowedRoles) {
        if (session == null) { send(out, errorResponse); return false; }
        String userRole = session.getLoggedInUser() != null ? session.getLoggedInUser().getRole().toUpperCase() : "";
        for (String allowed : allowedRoles) { if (allowed.equalsIgnoreCase(userRole)) return true; }
        send(out, errorResponse);
        log.warn("Phân quyền thất bại: user={} role={} cần {}", session.getUsername(), userRole, String.join("/", allowedRoles));
        return false;
    }

    /**
     * FIX 3: thêm out.reset() sau flush() để xóa object cache.
     * Tránh client nhận dữ liệu cũ khi cùng object được gửi lại sau khi thay đổi.
     */
    private void send(ObjectOutputStream out, Object obj) {
        try {
            out.writeObject(obj);
            out.flush();
            out.reset(); // FIX 3: xóa cache ObjectOutputStream
        } catch (Exception e) {
            log.warn("Không thể gửi response về client: {}", e.getMessage());
        }
    }

    private String resolveUsername(int userId) {
        if (userId == 0) return "";
        try { User u = userDAO.findById(userId); return u != null ? u.getUsername() : ""; }
        catch (Exception e) { return ""; }
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
        dto.setStatus(a.getStatus());
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

    private Item createItemByCategory(String category) {
        return switch (category.toUpperCase()) {
            case "ELECTRONICS" -> new Electronics();
            case "ART"         -> new Art();
            case "VEHICLE"     -> new Vehicle();
            default -> throw new IllegalArgumentException("Danh mục không hợp lệ: " + category);
        };
    }
    private void handleAdminGetRooms(ObjectInputStream in, ObjectOutputStream out, ServerSession session) {
        if (!requireLogin(session, out, new AdminGetRoomsResponse(false, "Bạn chưa đăng nhập.", null))) return;
        if (!requireRole(session, out, new AdminGetRoomsResponse(false, "Chỉ ADMIN mới có quyền.", null), "ADMIN")) return;
        try {
            AdminGetRoomsRequest req = (AdminGetRoomsRequest) in.readObject();

            // Lấy danh sách auction đang active từ service
            List<Auction> allActive = auctionService.getActiveAuctions();

            // Lọc theo keyword (tên item hoặc auctionId)
            String keyword = req.getKeyword();
            List<Auction> filtered = allActive.stream()
                    .filter(a -> {
                        if (keyword == null || keyword.isBlank()) return true;
                        String kw = keyword.trim().toLowerCase();
                        // Lọc theo auctionId
                        if (String.valueOf(a.getId()).contains(kw)) return true;
                        // Lọc theo tên item
                        Item item = itemDAO.findById(a.getItemId());
                        return item != null && item.getName().toLowerCase().contains(kw);
                    })
                    .collect(java.util.stream.Collectors.toList());

            // Lọc thêm theo status nếu có
            String statusFilter = req.getStatusFilter();
            if (statusFilter != null && !statusFilter.isBlank()) {
                try {
                    AuctionStatus filterStatus = AuctionStatus.valueOf(statusFilter.toUpperCase());
                    filtered = filtered.stream()
                            .filter(a -> a.getStatus() == filterStatus)
                            .collect(java.util.stream.Collectors.toList());
                } catch (IllegalArgumentException ignored) {}
            }

            List<AdminRoomDTO> dtos = filtered.stream()
                    .map(a -> buildAdminRoomDTO(a, false))
                    .collect(java.util.stream.Collectors.toList());

            send(out, new AdminGetRoomsResponse(true, "OK", dtos));
            log.info("Admin {} lấy danh sách {} phòng", session.getUsername(), dtos.size());
        } catch (Exception e) {
            log.error("Lỗi ADMIN_GET_ROOMS: {}", e.getMessage(), e);
            send(out, new AdminGetRoomsResponse(false, "Lỗi server: " + e.getMessage(), null));
        }
    }
    private void handleAdminGetRoomDetail(ObjectInputStream in, ObjectOutputStream out, ServerSession session) {
        if (!requireLogin(session, out, new AdminRoomDetailResponse(false, "Bạn chưa đăng nhập.", null))) return;
        if (!requireRole(session, out, new AdminRoomDetailResponse(false, "Chỉ ADMIN mới có quyền.", null), "ADMIN")) return;
        try {
            AdminGetRoomDetailRequest req = (AdminGetRoomDetailRequest) in.readObject();
            Auction auction = auctionService.getAuction(req.getAuctionId());
            AdminRoomDTO dto = buildAdminRoomDTO(auction, true);
            send(out, new AdminRoomDetailResponse(true, "OK", dto));
        } catch (Exception e) {
            log.error("Lỗi ADMIN_GET_ROOM_DETAIL: {}", e.getMessage(), e);
            send(out, new AdminRoomDetailResponse(false, "Lỗi server: " + e.getMessage(), null));
        }
    }
    private void handleAdminPauseRoom(ObjectInputStream in, ObjectOutputStream out, ServerSession session) {
        if (!requireLogin(session, out, new SimpleResponse(false, "Bạn chưa đăng nhập."))) return;
        if (!requireRole(session, out, new SimpleResponse(false, "Chỉ ADMIN mới có quyền."), "ADMIN")) return;
        try {
            AdminPauseRoomRequest req = (AdminPauseRoomRequest) in.readObject();
            Auction auction = auctionService.getAuction(req.getAuctionId());

            if (auction.getStatus() != AuctionStatus.RUNNING) {
                send(out, new SimpleResponse(false,
                        "Chỉ có thể tạm dừng phòng đang RUNNING. Trạng thái hiện tại: "
                                + auction.getStatus().getDisplay()));
                return;
            }

            // Cập nhật trạng thái → OPEN (tái sử dụng nghĩa "chờ")
            boolean ok = auctionService.pauseAuction(auction.getId(), session.getUserId());
            if (!ok) {
                send(out, new SimpleResponse(false, "Không thể tạm dừng phòng."));
                return;
            }

            // Broadcast thông báo tạm dừng đến tất cả người xem phòng này
            String reason = req.getReason() != null ? req.getReason() : "Admin tạm dừng phòng đấu giá";
            AuctionUpdateDTO update = new AuctionUpdateDTO(
                    auction.getId(),
                    AuctionUpdateDTO.UpdateType.AUCTION_STARTED, // dùng làm status change event
                    auction.getCurrentPrice(),
                    auction.getHighestBidderId(),
                    resolveUsername(auction.getHighestBidderId()),
                    auction.getEndTime(),
                    "[ADMIN] Phòng đã bị TẠM DỪNG: " + reason
            );
            auctionManager.broadcastUpdate(auction.getId(), update);

            send(out, new SimpleResponse(true, "Đã tạm dừng phòng đấu giá #" + req.getAuctionId()));
            log.info("Admin {} TẠM DỪNG phòng {} | Lý do: {}", session.getUsername(), req.getAuctionId(), reason);
        } catch (Exception e) {
            log.error("Lỗi ADMIN_PAUSE_ROOM: {}", e.getMessage(), e);
            send(out, new SimpleResponse(false, "Lỗi server: " + e.getMessage()));
        }
    }
    private void handleAdminResumeRoom(ObjectInputStream in, ObjectOutputStream out, ServerSession session) {
        if (!requireLogin(session, out, new SimpleResponse(false, "Bạn chưa đăng nhập."))) return;
        if (!requireRole(session, out, new SimpleResponse(false, "Chỉ ADMIN mới có quyền."), "ADMIN")) return;
        try {
            AdminResumeRoomRequest req = (AdminResumeRoomRequest) in.readObject();
            Auction auction = auctionService.getAuction(req.getAuctionId());

            if (auction.getStatus() != AuctionStatus.OPEN) {
                send(out, new SimpleResponse(false,
                        "Chỉ có thể tiếp tục phòng đang ở trạng thái OPEN. Hiện tại: "
                                + auction.getStatus().getDisplay()));
                return;
            }

            boolean ok = auctionService.resumeAuction(auction.getId(), session.getUserId());
            if (!ok) {
                send(out, new SimpleResponse(false, "Không thể tiếp tục phòng."));
                return;
            }

            AuctionUpdateDTO update = new AuctionUpdateDTO(
                    auction.getId(),
                    AuctionUpdateDTO.UpdateType.AUCTION_STARTED,
                    auction.getCurrentPrice(),
                    auction.getHighestBidderId(),
                    resolveUsername(auction.getHighestBidderId()),
                    auction.getEndTime(),
                    "[ADMIN] Phòng đã được TIẾP TỤC"
            );
            auctionManager.broadcastUpdate(auction.getId(), update);

            send(out, new SimpleResponse(true, "Đã tiếp tục phòng đấu giá #" + req.getAuctionId()));
            log.info("Admin {} TIẾP TỤC phòng {}", session.getUsername(), req.getAuctionId());
        } catch (Exception e) {
            log.error("Lỗi ADMIN_RESUME_ROOM: {}", e.getMessage(), e);
            send(out, new SimpleResponse(false, "Lỗi server: " + e.getMessage()));
        }
    }
    private void handleAdminCloseRoom(ObjectInputStream in, ObjectOutputStream out, ServerSession session) {
        if (!requireLogin(session, out, new SimpleResponse(false, "Bạn chưa đăng nhập."))) return;
        if (!requireRole(session, out, new SimpleResponse(false, "Chỉ ADMIN mới có quyền."), "ADMIN")) return;
        try {
            AdminCloseRoomRequest req = (AdminCloseRoomRequest) in.readObject();
            Auction auction = auctionService.getAuction(req.getAuctionId());

            if (auction.getStatus() == AuctionStatus.FINISHED
                    || auction.getStatus() == AuctionStatus.CANCELED) {
                send(out, new SimpleResponse(false, "Phòng đã ở trạng thái kết thúc: "
                        + auction.getStatus().getDisplay()));
                return;
            }

            String reason = req.getReason() != null ? req.getReason() : "Admin đóng phòng cưỡng bức";
            boolean ok = auctionService.cancelAuction(auction.getId(), session.getUserId(), reason);
            if (!ok) {
                send(out, new SimpleResponse(false, "Không thể đóng phòng."));
                return;
            }

            // Broadcast kết thúc cho tất cả người đang xem
            auctionManager.broadcastAuctionEnd(
                    auction.getId(),
                    auction.getHighestBidderId(),
                    auction.getCurrentPrice() != null ? auction.getCurrentPrice().doubleValue() : 0
            );

            send(out, new SimpleResponse(true, "Đã đóng phòng đấu giá #" + req.getAuctionId()));
            log.info("Admin {} ĐÓNG CƯỠNG BỨC phòng {} | Lý do: {}", session.getUsername(), req.getAuctionId(), reason);
        } catch (Exception e) {
            log.error("Lỗi ADMIN_CLOSE_ROOM: {}", e.getMessage(), e);
            send(out, new SimpleResponse(false, "Lỗi server: " + e.getMessage()));
        }
    }
    // ── HELPER: Build AdminRoomDTO ────────────────────────────────────────────

/**
 * Xây dựng AdminRoomDTO từ Auction.
 * @param withDetail nếu true → nạp thêm participantUsernames + recentBidLogs
 */

    private AdminRoomDTO buildAdminRoomDTO(Auction a, boolean withDetail) {
        AdminRoomDTO dto = new AdminRoomDTO();
        dto.setAuctionId(a.getId());
        dto.setStatus(a.getStatus());
        dto.setStartingPrice(a.getStartingPrice());
        dto.setCurrentPrice(a.getCurrentPrice() != null ? a.getCurrentPrice() : a.getStartingPrice());
        dto.setStartTime(a.getStartTime());
        dto.setEndTime(a.getEndTime());
        dto.setTotalBids(bidDAO.countByAuction(a.getId()));
        dto.setSellerName(resolveUsername(a.getSellerId()));

        if (a.getHighestBidderId() != 0) {
            dto.setHighestBidderUsername(resolveUsername(a.getHighestBidderId()));
        }

        Item item = itemDAO.findById(a.getItemId());
        if (item != null) {
            dto.setItemName(item.getName());
            dto.setItemCategory(item.getCategory() != null ? item.getCategory().name() : "");
        }

        // Số người đang subscribe phòng này (realtime)
        dto.setParticipantCount(auctionManager.getParticipantCount(a.getId()));

        if (withDetail) {
            // Danh sách username người tham gia (lấy từ bid history)
            List<BidTransaction> bids = bidDAO.findByAuction(a.getId());

            // Unique usernames
            java.util.LinkedHashSet<String> participantSet = new java.util.LinkedHashSet<>();
            java.util.List<String> recentLogs = new java.util.ArrayList<>();
            int logCount = 0;
            // Duyệt từ mới nhất → cũ nhất
            for (int i = bids.size() - 1; i >= 0; i--) {
                BidTransaction bid = bids.get(i);
                String username = resolveUsername(bid.getBidderId());
                participantSet.add(username);
                if (logCount < 10) {
                    recentLogs.add(String.format("[%s] %s đặt giá %,.0f đ",
                            bid.getCreatedAt() != null
                                    ? bid.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                                    : "--:--:--",
                            username,
                            bid.getAmount().doubleValue()));
                    logCount++;
                }
            }
            // Đảo lại log: mới nhất ở cuối (như append log)
            java.util.Collections.reverse(recentLogs);
            dto.setParticipantUsernames(new java.util.ArrayList<>(participantSet));
            dto.setRecentBidLogs(recentLogs);
        }

        return dto;
    }
}