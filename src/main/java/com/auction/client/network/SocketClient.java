package com.auction.client.network;

import com.auction.common.request.*;
import com.auction.common.response.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * SocketClient - Singleton quản lý kết nối socket từ Client đến Server.
 *
 * Cách dùng trong controller:
 *   LoginResponse res = SocketClient.getInstance().login(request);
 *
 * Giao thức:
 *   1. Client gửi action (String)
 *   2. Client gửi request object
 *   3. Client nhận response object
 *
 * Tất cả method đều synchronized để an toàn khi nhiều luồng JavaFX dùng chung.
 */
public class SocketClient {

    // ── Cấu hình kết nối ─────────────────────────────────────
    private static final String DEFAULT_HOST = "localhost";
    private static final int    DEFAULT_PORT = 8080;

    // ── Singleton ─────────────────────────────────────────────
    private static volatile SocketClient instance;

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

    // ── State ─────────────────────────────────────────────────
    private final String host;
    private final int    port;

    private Socket            socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;

    // ── Constructor ───────────────────────────────────────────
    private SocketClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // ── Kết nối / Ngắt kết nối ───────────────────────────────

    /**
     * Kết nối đến server. Gọi một lần khi ứng dụng khởi động.
     */
    public synchronized void connect() throws IOException {
        if (isConnected()) return;
        socket = new Socket(host, port);
        // ObjectOutputStream TRƯỚC, ObjectInputStream SAU (đồng bộ với server)
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in  = new ObjectInputStream(socket.getInputStream());
        System.out.println("[SocketClient] Đã kết nối đến " + host + ":" + port);
    }

    /**
     * Ngắt kết nối an toàn. Gọi khi đóng ứng dụng hoặc logout.
     */
    public synchronized void disconnect() {
        try { if (in     != null) in.close();  } catch (Exception ignored) {}
        try { if (out    != null) out.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        in = null; out = null; socket = null;
        System.out.println("[SocketClient] Đã ngắt kết nối.");
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * Tự động kết nối lại nếu bị mất kết nối.
     */
    private synchronized void ensureConnected() throws IOException {
        if (!isConnected()) {
            System.out.println("[SocketClient] Mất kết nối — đang kết nối lại...");
            connect();
        }
    }

    // ── API cho các Controller ────────────────────────────────

    /**
     * Gửi yêu cầu đăng nhập.
     */
    public synchronized LoginResponse login(LoginRequest request) {
        try {
            ensureConnected();
            out.writeObject("USER_LOGIN");
            out.writeObject(request);
            out.flush();
            return (LoginResponse) in.readObject();
        } catch (Exception e) {
            System.err.println("[SocketClient] Lỗi USER_LOGIN: " + e.getMessage());
            return new LoginResponse(false, "Không thể kết nối đến máy chủ: " + e.getMessage(), null);
        }
    }

    /**
     * Gửi yêu cầu đăng ký.
     */
    public synchronized RegisterResponse register(RegisterRequest request) {
        try {
            ensureConnected();
            out.writeObject("USER_REGISTER");
            out.writeObject(request);
            out.flush();
            return (RegisterResponse) in.readObject();
        } catch (Exception e) {
            System.err.println("[SocketClient] Lỗi USER_REGISTER: " + e.getMessage());
            return new RegisterResponse(false, "Không thể kết nối đến máy chủ: " + e.getMessage(), null);
        }
    }

    /**
     * Gửi yêu cầu cập nhật profile.
     */
    public synchronized UpdateProfileResponse updateProfile(UpdateProfileRequest request) {
        try {
            ensureConnected();
            out.writeObject("USER_UPDATE_PROFILE");
            out.writeObject(request);
            out.flush();
            return (UpdateProfileResponse) in.readObject();
        } catch (Exception e) {
            System.err.println("[SocketClient] Lỗi USER_UPDATE_PROFILE: " + e.getMessage());
            return new UpdateProfileResponse(false, "Không thể kết nối đến máy chủ: " + e.getMessage(), null);
        }
    }

    /**
     * Gửi yêu cầu nạp/rút tiền.
     */
    public synchronized BalanceResponse updateBalance(BalanceRequest request) {
        try {
            ensureConnected();
            out.writeObject("USER_BALANCE");
            out.writeObject(request);
            out.flush();
            return (BalanceResponse) in.readObject();
        } catch (Exception e) {
            System.err.println("[SocketClient] Lỗi USER_BALANCE: " + e.getMessage());
            return new BalanceResponse(false, "Không thể kết nối đến máy chủ: " + e.getMessage(), null);
        }
    }
    public CreateAuctionResponse createAuctionResponse(CreateAuctionRequest request){
        try{
            ensureConnected();
            out.writeObject("AUCTION_CREATE");
            out.writeObject(request);
            out.flush();
            return(CreateAuctionResponse) in.readObject();
        }catch (Exception e){
            return new CreateAuctionResponse(false,"Lỗi kết nối"+e.getMessage(),null);
        }
    }
    public synchronized GetPendingAuctionRequestsResponse getPendingAuctionRequests(
            GetPendingAuctionRequestsRequest request
    ) {

        try {

            ensureConnected();

            // gửi action
            out.writeObject(
                    "AUCTION_GET_PENDING_REQUESTS"
            );

            // gửi request object
            out.writeObject(request);

            out.flush();

            // đọc response
            return (GetPendingAuctionRequestsResponse)
                    in.readObject();

        } catch (Exception e) {

            System.err.println(
                    "[SocketClient] Lỗi AUCTION_GET_PENDING_REQUESTS: "
                            + e.getMessage()
            );

            return new GetPendingAuctionRequestsResponse(
                    false,
                    "Không thể kết nối đến máy chủ: "
                            + e.getMessage(),
                    null
            );
        }
    }
}
