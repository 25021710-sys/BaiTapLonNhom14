package com.auction.client.network;

import com.auction.common.dto.AuctionUpdateDTO;
import com.auction.common.request.*;
import com.auction.common.response.*;
import com.auction.server.model.AutoBidConfig;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * SocketClient - Singleton quản lý kết nối socket từ Client đến Server.
 *
 * Giao thức:
 *   1. Client gửi action (String)
 *   2. Client gửi request object (nếu cần)
 *   3. Client nhận response object
 *
 * FIX RACE CONDITION:
 *   - Push listener là LUỒNG DUY NHẤT đọc từ `in`.
 *   - Các response thông thường được route vào `responseQueue`.
 *   - Các method request/response đọc từ `responseQueue` thay vì đọc `in` trực tiếp.
 *   - AUCTION_PUSH_UPDATE được route đến `pushCallback`.
 *   Điều này ngăn push listener và request/response cùng tranh nhau đọc `in`.
 */
public class SocketClient {

    private static final String DEFAULT_HOST = "localhost";
    private static final int    DEFAULT_PORT = 8080;
    private static final int    RESPONSE_TIMEOUT_MS = 10_000; // 10 giây

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static volatile SocketClient instance;
    private final Object requestLock = new Object();
    public static SocketClient getInstance() {
        if (instance == null) {
            synchronized (SocketClient.class) {
                if (instance == null) {
                    instance = new SocketClient(DEFAULT_HOST, DEFAULT_PORT);
                }
            }
        }
        return instance;
    }

    private final String host;
    private final int    port;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;

    // Hàng đợi chứa response thông thường (không phải push update)
    private final LinkedBlockingQueue<Object> responseQueue = new LinkedBlockingQueue<>();

    // Callback nhận realtime update từ server (được set bởi AuctionRoomController)
    private volatile Consumer<AuctionUpdateDTO> pushCallback;

    private SocketClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // ── Kết nối ───────────────────────────────────────────────────────────────

    public void connect() throws IOException {
        synchronized (requestLock) {
            if (isConnected()) return;
            socket = new Socket(host, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in  = new ObjectInputStream(socket.getInputStream());
            startPushListener();
            System.out.println("[SocketClient] Đã kết nối đến " + host + ":" + port);
        }
    }

    public void disconnect() {
        synchronized (requestLock) {
            try { if (in != null) in.close();     } catch (Exception ignored) {}
            try { if (out != null) out.close();   } catch (Exception ignored) {}
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
            in = null; out = null; socket = null;
            responseQueue.clear();
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    private void ensureConnected() throws IOException {
        // Gọi trong requestLock nên không cần synchronized riêng
        if (!isConnected()) {
            System.out.println("[SocketClient] Mất kết nối — đang kết nối lại...");
            socket = new Socket(host, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in  = new ObjectInputStream(socket.getInputStream());
            startPushListener();
        }
    }

    /**
     * Đăng ký callback nhận push update từ server (realtime bid updates).
     */
    public void setPushCallback(Consumer<AuctionUpdateDTO> callback) {
        this.pushCallback = callback;
    }

    /**
     * Luồng lắng nghe tất cả dữ liệu từ server.
     * Là LUỒNG DUY NHẤT được phép đọc từ `in`.
     *
     * - AUCTION_PUSH_UPDATE → gọi pushCallback
     * - Mọi object khác      → đẩy vào responseQueue để các method request/response lấy
     */
    private void startPushListener() {
        Thread t = new Thread(() -> {
            while (isConnected()) {
                try {
                    Object obj = in.readObject();
                    System.out.println("CLIENT RECEIVED: " + obj);
                    if ("AUCTION_PUSH_UPDATE".equals(obj)) {
                        AuctionUpdateDTO update = (AuctionUpdateDTO) in.readObject();
                        Consumer<AuctionUpdateDTO> cb = pushCallback;
                        if (cb != null) {
                            javafx.application.Platform.runLater(() -> cb.accept(update));
                        }
                    } else {
                        // Response thông thường → đưa vào queue cho method đang chờ
                        responseQueue.put(obj);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("=== PUSH LISTENER CRASH ===");
                    e.printStackTrace();
                    break;
                }
            }
        }, "socket-push-listener");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Đọc response từ queue thay vì đọc trực tiếp `in`.
     * Giải quyết race condition: push listener là reader duy nhất của `in`.
     */
    @SuppressWarnings("unchecked")
    private <T> T readResponse() throws Exception {
        Object resp = responseQueue.poll(RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (resp == null) throw new IOException("Timeout chờ response từ server (" + RESPONSE_TIMEOUT_MS + "ms)");
        return (T) resp;
    }

    // ── USER ──────────────────────────────────────────────────────────────────

    public LoginResponse login(LoginRequest request) {
        synchronized (requestLock) {
            try {
                ensureConnected();
                out.writeObject("USER_LOGIN");
                out.writeObject(request);
                out.flush();
                return readResponse();
            } catch (Exception e) {
                return new LoginResponse(false, "Không thể kết nối đến máy chủ: " + e.getMessage(), null);
            }
        }
    }

    public RegisterResponse register(RegisterRequest request) {
        synchronized (requestLock) {
            try {
                ensureConnected();
                out.writeObject("USER_REGISTER");
                out.writeObject(request);
                out.flush();
                return readResponse();
            } catch (Exception e) {
                return new RegisterResponse(false, "Không thể kết nối đến máy chủ: " + e.getMessage(), null);
            }
        }
    }

    public UpdateProfileResponse updateProfile(UpdateProfileRequest request) {
        synchronized (requestLock) {
            try {
                ensureConnected();
                out.writeObject("USER_UPDATE_PROFILE");
                out.writeObject(request);
                out.flush();
                return readResponse();
            } catch (Exception e) {
                return new UpdateProfileResponse(false, "Không thể kết nối đến máy chủ: " + e.getMessage(), null);
            }
        }
    }

    public BalanceResponse updateBalance(BalanceRequest request) {
        synchronized (requestLock) {
            try {
                ensureConnected();
                out.writeObject("USER_BALANCE");
                out.writeObject(request);
                out.flush();
                return readResponse();
            } catch (Exception e) {
                return new BalanceResponse(false, "Không thể kết nối đến máy chủ: " + e.getMessage(), null);
            }
        }
    }

    public CreateAuctionResponse createAuction(CreateAuctionRequest request) {
        synchronized (requestLock) {
            try {
                ensureConnected();
                out.writeObject("AUCTION_CREATE");
                out.writeObject(request);
                out.flush();
                return readResponse();
            } catch (Exception e) {
                return new CreateAuctionResponse(false, "Lỗi kết nối: " + e.getMessage(), null);
            }
        }
    }

    public AuctionListResponse getActiveAuctions() {
        synchronized (requestLock) {
            try {
                ensureConnected();
                out.writeObject("AUCTION_GET_ACTIVE");
                out.flush();
                return readResponse();
            } catch (Exception e) {
                return new AuctionListResponse(false, "Lỗi kết nối: " + e.getMessage(), null);
            }
        }
    }

    public GetPendingAuctionRequestsResponse getPendingAuctionRequests(
            GetPendingAuctionRequestsRequest request) {
        synchronized (requestLock) {
            try {
                ensureConnected();
                out.writeObject("AUCTION_GET_PENDING_REQUESTS");
                out.writeObject(request);
                out.flush();
                return readResponse();
            } catch (Exception e) {
                return new GetPendingAuctionRequestsResponse(false, "Lỗi kết nối: " + e.getMessage(), null);
            }
        }
    }

    public ApproveAuctionResponse approveAuction(ApproveAuctionRequest request) {
        synchronized (requestLock) {
            try {
                ensureConnected();
                out.writeObject("AUCTION_APPROVE");
                out.writeObject(request);
                out.flush();
                return readResponse();
            } catch (Exception e) {
                return new ApproveAuctionResponse(false, "Lỗi kết nối: " + e.getMessage());
            }
        }
    }

    public RejectAuctionResponse rejectAuction(RejectAuctionRequest request) {
        synchronized (requestLock) {
            try {
                ensureConnected();
                out.writeObject("AUCTION_REJECT");
                out.writeObject(request);
                out.flush();
                return readResponse();
            } catch (Exception e) {
                return new RejectAuctionResponse(false, "Lỗi kết nối: " + e.getMessage());
            }
        }
    }

    public CreateAuctionResponse subscribeAuction(int auctionId) {
        synchronized (requestLock) {
            try {
                ensureConnected();
                out.writeObject("AUCTION_SUBSCRIBE");
                out.writeInt(auctionId);
                out.flush();
                return readResponse();
            } catch (Exception e) {
                return new CreateAuctionResponse(false, "Lỗi kết nối: " + e.getMessage(), null);
            }
        }
    }

    public void unsubscribeAuction(int auctionId) {
        synchronized (requestLock) {
            try {
                ensureConnected();
                out.writeObject("AUCTION_UNSUBSCRIBE");
                out.writeInt(auctionId);
                out.flush();
                readResponse();
            } catch (Exception ignored) {}
        }
    }

    public BidResponse placeBid(BidRequest request) {
        synchronized (requestLock) {
            try {
                ensureConnected();
                out.writeObject("BID_PLACE");
                out.writeObject(request);
                out.flush();
                return readResponse();
            } catch (Exception e) {
                return new BidResponse(false, "Lỗi kết nối: " + e.getMessage(), java.math.BigDecimal.ZERO);
            }
        }
    }

    public BidHistoryResponse getBidHistory(int auctionId) {
        synchronized (requestLock) {
            try {
                ensureConnected();
                out.writeObject("AUCTION_GET_BIDS");
                out.writeInt(auctionId);
                out.flush();
                return readResponse();
            } catch (Exception e) {
                return new BidHistoryResponse(false, "Lỗi kết nối: " + e.getMessage(), null);
            }
        }
    }

    public SimpleResponse registerAutoBid(AutoBidConfig config) {
        synchronized (requestLock) {
            try {
                ensureConnected();
                out.writeObject("AUTOBID_REGISTER");
                out.writeObject(config);
                out.flush();
                return readResponse();
            } catch (Exception e) {
                return new SimpleResponse(false, "Lỗi kết nối: " + e.getMessage());
            }
        }
    }

    public SimpleResponse cancelAutoBid(int bidderId, int auctionId) {
        synchronized (requestLock) {
            try {
                ensureConnected();
                out.writeObject("AUTOBID_CANCEL");
                out.writeInt(bidderId);
                out.writeInt(auctionId);
                out.flush();
                return readResponse();
            } catch (Exception e) {
                return new SimpleResponse(false, "Lỗi kết nối: " + e.getMessage());
            }
        }
    }
}