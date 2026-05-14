package com.auction.client.network;

import com.auction.common.dto.AuctionUpdateDTO;
import com.auction.common.request.*;
import com.auction.common.response.*;
import com.auction.server.model.AutoBidConfig;
import com.auction.common.dto.AdminRoomDTO;
import com.auction.common.request.AdminGetRoomsRequest;
import com.auction.common.request.AdminGetRoomDetailRequest;
import com.auction.common.request.AdminPauseRoomRequest;
import com.auction.common.request.AdminResumeRoomRequest;
import com.auction.common.request.AdminCloseRoomRequest;
import com.auction.common.response.AdminGetRoomsResponse;
import com.auction.common.response.AdminRoomDetailResponse;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * SocketClient – Singleton quản lý kết nối socket từ Client đến Server.
 *
 * FIX 1 – DEADLOCK (Timeout 10s):
 *   Dùng writeLock thay synchronized trên method.
 *   Lock chỉ giữ khi GHI ra `out`, thả trước khi chờ response.
 *   Push listener đọc từ `in` hoàn toàn độc lập, không bao giờ bị block.
 *
 * FIX 2 – ClassCastException (AuctionListResponse cannot be cast to CreateAuctionResponse):
 *   Dùng readTypedResponse(Class<T>) để lọc đúng kiểu từ queue.
 *   Nếu queue có response sai kiểu (stale từ request khác), bỏ qua và đọc tiếp.
 */
public class SocketClient {

    private static final String DEFAULT_HOST        = "localhost";
    private static final int    DEFAULT_PORT        = 8080;
    private static final int    RESPONSE_TIMEOUT_MS = 10_000;

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static volatile SocketClient instance;

    public static SocketClient getInstance() {
        if (instance == null) {
            synchronized (SocketClient.class) {
                if (instance == null) instance = new SocketClient(DEFAULT_HOST, DEFAULT_PORT);
            }
        }
        return instance;
    }

    private final String host;
    private final int    port;

    private Socket             socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;

    // Lock CHỈ cho phần ghi ra `out` – push listener đọc `in` không cần lock này
    private final ReentrantLock writeLock = new ReentrantLock();

    // Queue nhận mọi response từ server (trừ PUSH_UPDATE đã tách riêng)
    private final LinkedBlockingQueue<Object> responseQueue = new LinkedBlockingQueue<>();

    private volatile Consumer<AuctionUpdateDTO> pushCallback;

    private SocketClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // ── Kết nối ───────────────────────────────────────────────────────────────

    public synchronized void connect() throws IOException {
        if (isConnected()) return;
        socket = new Socket(host, port);
        out    = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in     = new ObjectInputStream(socket.getInputStream());
        responseQueue.clear();
        startPushListener();
        System.out.println("[SocketClient] Đã kết nối đến " + host + ":" + port);
    }

    public synchronized void disconnect() {
        try { if (in     != null) in.close();     } catch (Exception ignored) {}
        try { if (out    != null) out.close();    } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        in = null; out = null; socket = null;
        responseQueue.clear();
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    private synchronized void ensureConnected() throws IOException {
        if (!isConnected()) {
            System.out.println("[SocketClient] Mất kết nối — đang kết nối lại...");
            connect();
        }
    }

    public void setPushCallback(Consumer<AuctionUpdateDTO> callback) {
        this.pushCallback = callback;
    }

    // ── Push listener ─────────────────────────────────────────────────────────

    /**
     * Luồng DUY NHẤT đọc từ `in`. Không giữ writeLock.
     * PUSH_UPDATE → gọi callback trên FX thread.
     * Response thường → đẩy vào responseQueue.
     */
    private void startPushListener() {
        Thread t = new Thread(() -> {
            while (isConnected()) {
                try {
                    Object obj = in.readObject();
                    if ("AUCTION_PUSH_UPDATE".equals(obj)) {
                        AuctionUpdateDTO update = (AuctionUpdateDTO) in.readObject();
                        Consumer<AuctionUpdateDTO> cb = pushCallback;
                        if (cb != null) {
                            javafx.application.Platform.runLater(() -> cb.accept(update));
                        }
                    } else {
                        responseQueue.put(obj);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    if (isConnected())
                        System.err.println("[SocketClient] Push listener lỗi: " + e.getMessage());
                    break;
                }
            }
        }, "socket-push-listener");
        t.setDaemon(true);
        t.start();
    }

    // ── Read helpers ──────────────────────────────────────────────────────────

    /**
     * FIX 2: Đọc response đúng kiểu expectedType, bỏ qua object sai kiểu.
     * Ngăn ClassCastException khi nhiều request đồng thời đưa response vào queue.
     */
    @SuppressWarnings("unchecked")
    private <T> T readTypedResponse(Class<T> expectedType) throws Exception {
        long deadline = System.currentTimeMillis() + RESPONSE_TIMEOUT_MS;
        while (true) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0)
                throw new IOException("Timeout chờ response từ server (" + RESPONSE_TIMEOUT_MS + "ms)");
            Object resp = responseQueue.poll(remaining, TimeUnit.MILLISECONDS);
            if (resp == null)
                throw new IOException("Timeout chờ response từ server (" + RESPONSE_TIMEOUT_MS + "ms)");
            if (expectedType.isInstance(resp))
                return (T) resp;
            // Sai kiểu → bỏ qua, đọc tiếp trong deadline còn lại
            System.err.println("[SocketClient] Bỏ qua response sai kiểu: "
                    + resp.getClass().getSimpleName()
                    + " (cần " + expectedType.getSimpleName() + ")");
        }
    }

    /** Đọc không lọc kiểu – dùng cho unsubscribe, cancelAutoBid. */
    @SuppressWarnings("unchecked")
    private <T> T readResponse() throws Exception {
        Object resp = responseQueue.poll(RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (resp == null)
            throw new IOException("Timeout chờ response từ server (" + RESPONSE_TIMEOUT_MS + "ms)");
        return (T) resp;
    }

    // ── Send helpers ──────────────────────────────────────────────────────────

    /** Ghi action + payload. Lock CHỈ khi ghi, thả trước khi đọc response. */
    private void sendRequest(String action, Object payload) throws Exception {
        ensureConnected();
        writeLock.lock();
        try {
            out.writeObject(action);
            if (payload != null) out.writeObject(payload);
            out.flush();
        } finally {
            writeLock.unlock();
        }
    }

    private void sendRequestWithInt(String action, int value) throws Exception {
        ensureConnected();
        writeLock.lock();
        try {
            out.writeObject(action);
            out.writeInt(value);
            out.flush();
        } finally {
            writeLock.unlock();
        }
    }

    private void sendRequest(String action) throws Exception {
        sendRequest(action, null);
    }

    // ── USER ──────────────────────────────────────────────────────────────────

    public LoginResponse login(LoginRequest request) {
        try { sendRequest("USER_LOGIN", request); return readTypedResponse(LoginResponse.class); }
        catch (Exception e) { return new LoginResponse(false, "Không thể kết nối: " + e.getMessage(), null); }
    }

    public RegisterResponse register(RegisterRequest request) {
        try { sendRequest("USER_REGISTER", request); return readTypedResponse(RegisterResponse.class); }
        catch (Exception e) { return new RegisterResponse(false, "Không thể kết nối: " + e.getMessage(), null); }
    }

    public UpdateProfileResponse updateProfile(UpdateProfileRequest request) {
        try { sendRequest("USER_UPDATE_PROFILE", request); return readTypedResponse(UpdateProfileResponse.class); }
        catch (Exception e) { return new UpdateProfileResponse(false, "Không thể kết nối: " + e.getMessage(), null); }
    }

    public BalanceResponse updateBalance(BalanceRequest request) {
        try { sendRequest("USER_BALANCE", request); return readTypedResponse(BalanceResponse.class); }
        catch (Exception e) { return new BalanceResponse(false, "Không thể kết nối: " + e.getMessage(), null); }
    }

    // ── AUCTION ───────────────────────────────────────────────────────────────

    public CreateAuctionResponse createAuction(CreateAuctionRequest request) {
        try { sendRequest("AUCTION_CREATE", request); return readTypedResponse(CreateAuctionResponse.class); }
        catch (Exception e) { return new CreateAuctionResponse(false, "Lỗi kết nối: " + e.getMessage(), null); }
    }

    public AuctionListResponse getActiveAuctions() {
        try { sendRequest("AUCTION_GET_ACTIVE"); return readTypedResponse(AuctionListResponse.class); }
        catch (Exception e) { return new AuctionListResponse(false, "Lỗi kết nối: " + e.getMessage(), null); }
    }

    public GetPendingAuctionRequestsResponse getPendingAuctionRequests(GetPendingAuctionRequestsRequest request) {
        try { sendRequest("AUCTION_GET_PENDING_REQUESTS", request); return readTypedResponse(GetPendingAuctionRequestsResponse.class); }
        catch (Exception e) { return new GetPendingAuctionRequestsResponse(false, "Lỗi kết nối: " + e.getMessage(), null); }
    }

    public ApproveAuctionResponse approveAuction(ApproveAuctionRequest request) {
        try { sendRequest("AUCTION_APPROVE", request); return readTypedResponse(ApproveAuctionResponse.class); }
        catch (Exception e) { return new ApproveAuctionResponse(false, "Lỗi kết nối: " + e.getMessage()); }
    }

    public RejectAuctionResponse rejectAuction(RejectAuctionRequest request) {
        try { sendRequest("AUCTION_REJECT", request); return readTypedResponse(RejectAuctionResponse.class); }
        catch (Exception e) { return new RejectAuctionResponse(false, "Lỗi kết nối: " + e.getMessage()); }
    }

    public CreateAuctionResponse subscribeAuction(int auctionId) {
        try { sendRequestWithInt("AUCTION_SUBSCRIBE", auctionId); return readTypedResponse(CreateAuctionResponse.class); }
        catch (Exception e) { return new CreateAuctionResponse(false, "Lỗi kết nối: " + e.getMessage(), null); }
    }

    public void unsubscribeAuction(int auctionId) {
        try { sendRequestWithInt("AUCTION_UNSUBSCRIBE", auctionId); readResponse(); }
        catch (Exception ignored) {}
    }

    public BidResponse placeBid(BidRequest request) {
        try { sendRequest("BID_PLACE", request); return readTypedResponse(BidResponse.class); }
        catch (Exception e) { return new BidResponse(false, "Lỗi kết nối: " + e.getMessage(), java.math.BigDecimal.ZERO); }
    }

    public BidHistoryResponse getBidHistory(int auctionId) {
        try { sendRequestWithInt("AUCTION_GET_BIDS", auctionId); return readTypedResponse(BidHistoryResponse.class); }
        catch (Exception e) { return new BidHistoryResponse(false, "Lỗi kết nối: " + e.getMessage(), null); }
    }

    // ── AUTO BID ──────────────────────────────────────────────────────────────

    public SimpleResponse registerAutoBid(AutoBidConfig config) {
        try { sendRequest("AUTOBID_REGISTER", config); return readTypedResponse(SimpleResponse.class); }
        catch (Exception e) { return new SimpleResponse(false, "Lỗi kết nối: " + e.getMessage()); }
    }

    public SimpleResponse cancelAutoBid(int bidderId, int auctionId) {
        try {
            ensureConnected();
            writeLock.lock();
            try {
                out.writeObject("AUTOBID_CANCEL");
                out.writeInt(bidderId);
                out.writeInt(auctionId);
                out.flush();
            } finally {
                writeLock.unlock();
            }
            return readResponse();
        } catch (Exception e) {
            return new SimpleResponse(false, "Lỗi kết nối: " + e.getMessage());
        }
    }

    public AuctionListResponse getMyAuctions() {
        try {
            sendRequest("AUCTION_GET_MY");
            return readTypedResponse(AuctionListResponse.class);
        } catch (Exception e) {
            return new AuctionListResponse(false, "Lỗi kết nối: " + e.getMessage(), null);
        }
    }

    public AuctionListResponse getJoinedAuctions() {
        try {
            sendRequest("AUCTION_GET_JOINED");
            return readTypedResponse(AuctionListResponse.class);
        } catch (Exception e) {
            return new AuctionListResponse(false, "Lỗi kết nối: " + e.getMessage(), null);
        }
    }

    public synchronized AuctionListResponse getDashboardAuctions() {
        try {
            sendRequest("AUCTION_GET_DASHBOARD");
            return readResponse();
        } catch (Exception e) {
            return new AuctionListResponse(false, "Lỗi kết nối: " + e.getMessage(), null);
        }
    }

    public synchronized GetUserProfileResponse getSellerProfile(String username) {
        try {
            sendRequest("USER_GET_PROFILE_BY_USERNAME");
            out.writeObject(username);
            out.flush();
            return readResponse();
        } catch (Exception e) {
            return new GetUserProfileResponse(false, "Loi ket noi", null, 0);
        }
    }
    public AdminGetRoomsResponse adminGetRooms(String statusFilter, String keyword) {
        try {
            sendRequest("ADMIN_GET_ROOMS", new AdminGetRoomsRequest(statusFilter, keyword));
            return readTypedResponse(AdminGetRoomsResponse.class);
        } catch (Exception e) {
            return new AdminGetRoomsResponse(false, "Lỗi kết nối: " + e.getMessage(), null);
        }
    }
    public AdminRoomDetailResponse adminGetRoomDetail(int auctionId) {
        try {
            sendRequest("ADMIN_GET_ROOM_DETAIL", new AdminGetRoomDetailRequest(auctionId));
            return readTypedResponse(AdminRoomDetailResponse.class);
        } catch (Exception e) {
            return new AdminRoomDetailResponse(false, "Lỗi kết nối: " + e.getMessage(), null);
        }
    }
    public SimpleResponse adminPauseRoom(int auctionId, String reason) {
        try {
            sendRequest("ADMIN_PAUSE_ROOM", new AdminPauseRoomRequest(auctionId, reason));
            return readTypedResponse(SimpleResponse.class);
        } catch (Exception e) {
            return new SimpleResponse(false, "Lỗi kết nối: " + e.getMessage());
        }
    }
    public SimpleResponse adminResumeRoom(int auctionId) {
        try {
            sendRequest("ADMIN_RESUME_ROOM", new AdminResumeRoomRequest(auctionId));
            return readTypedResponse(SimpleResponse.class);
        } catch (Exception e) {
            return new SimpleResponse(false, "Lỗi kết nối: " + e.getMessage());
        }
    }
    public SimpleResponse adminCloseRoom(int auctionId, String reason) {
        try {
            sendRequest("ADMIN_CLOSE_ROOM", new AdminCloseRoomRequest(auctionId, reason));
            return readTypedResponse(SimpleResponse.class);
        } catch (Exception e) {
            return new SimpleResponse(false, "Lỗi kết nối: " + e.getMessage());
        }
    }
}