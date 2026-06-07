package com.auction.server.network;

import com.auction.common.dto.AuctionUpdateDTO;
import com.auction.server.controller.AuctionController;
import com.auction.server.controller.ItemController;
import com.auction.server.controller.UserController;
import com.auction.server.observer.AuctionObserver;
import com.auction.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * ClientHandler – xử lý kết nối của MỘT client trên một luồng riêng.
 *
 * FIX 1 – out.reset() sau mỗi response thông thường:
 *   ObjectOutputStream cache reference các object đã ghi. Nếu không reset(),
 *   lần sau ghi cùng object (ví dụ AuctionDTO với id giống) sẽ chỉ ghi
 *   "reference đến object cũ" → client nhận dữ liệu cũ (stale).
 *   onAuctionUpdate() đã có reset() — giờ thêm vào send() luồng thường.
 *
 * FIX 2 – AuctionController, ItemController dùng shared instance từ SocketServer:
 *   Bản cũ: new AuctionController() trong constructor → mỗi client có instance riêng
 *   → pendingDtoCache không được share giữa các client.
 *   Fix: nhận controller từ bên ngoài (dependency injection qua constructor).
 */
public class ClientHandler implements Runnable, AuctionObserver {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    // ── Registry: userId → ClientHandler đang online ──────────────────────────
    // Dùng để server push thẳng đến 1 user cụ thể (ví dụ cộng tiền seller)
    private static final java.util.concurrent.ConcurrentHashMap<Integer, ClientHandler>
            onlineHandlers = new java.util.concurrent.ConcurrentHashMap<>();

    /** Tìm handler đang online của userId, null nếu user không online. */
    public static ClientHandler findOnline(int userId) {
        return onlineHandlers.get(userId);
    }

    /** Push AuctionUpdateDTO đến một userId cụ thể (nếu đang online). */
    public static void pushToUser(int userId, com.auction.common.dto.AuctionUpdateDTO update) {
        ClientHandler h = onlineHandlers.get(userId);
        if (h != null) h.onAuctionUpdate(update);
    }

    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;

    private final ServerSession     session           = new ServerSession();
    // Controllers được inject từ SocketServer (shared instances)
    private final UserController    userController;
    private final ItemController    itemController;
    private final AuctionController auctionController;

    public ServerSession getSession() { return session; }

    /**
     * FIX 2: Constructor nhận shared controller thay vì tự tạo mới.
     * UserController vẫn tạo riêng vì nó giữ ServerSession của kết nối này.
     */
    public ClientHandler(Socket socket,
                         ItemController itemController,
                         AuctionController auctionController) {
        this.socket            = socket;
        this.userController    = new UserController(session);  // per-connection (cần session)
        this.itemController    = itemController;               // shared
        this.auctionController = auctionController;            // shared
    }

    @Override
    public void run() {
        String clientAddr = socket.getInetAddress().getHostAddress();
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in  = new ObjectInputStream(socket.getInputStream());

            log.info("[{}] Kết nối thành công.", clientAddr);

            while (true) {
                Object obj = in.readObject();

                if (obj instanceof String action) {
                    log.info("[{}] Yêu cầu: {}", clientAddr, action);

                    String prefix = action.contains("_") ? action.split("_")[0] : action;

                    switch (prefix) {
                        case "USER"                -> userController.processRequest(action, in, out);
                        case "ITEM"                -> itemController.processRequest(action, in, out);
                        case "BID", "AUCTION", "ADMIN" ->
                                auctionController.processRequest(action, in, out, session, this);
                        default -> {
                            log.warn("[{}] Action không xác định: {}", clientAddr, action);
                            synchronized (this) {
                                out.writeObject("ERROR_UNKNOWN_ACTION");
                                out.flush();
                                out.reset(); // FIX 1
                            }
                        }
                    }

                    // Sau mỗi request, nếu user vừa đăng nhập → đăng ký vào registry
                    int uid = session.getUserId();
                    if (uid != -1) onlineHandlers.put(uid, this);
                }
            }

        } catch (EOFException e) {
            log.info("[{}] Client ngắt kết nối. ({})", clientAddr, session);
        } catch (Exception e) {
            log.error("[{}] Lỗi kết nối: {} ({})", clientAddr, e.getMessage(), session);
        } finally {
            // Hủy đăng ký khỏi registry khi ngắt kết nối
            int uid = session.getUserId();
            if (uid != -1) onlineHandlers.remove(uid);
            session.logout();
            closeConnections();
        }
    }

    /**
     * Observer callback: push AuctionUpdateDTO về client.
     * FIX 1: out.reset() đã có ở đây — giữ nguyên.
     */
    @Override
    public synchronized void onAuctionUpdate(AuctionUpdateDTO update) {
        try {
            if (out != null && !socket.isClosed()) {
                out.writeObject("AUCTION_PUSH_UPDATE");
                out.writeObject(update);
                out.flush();
                out.reset(); // xóa object cache sau push
            }
        } catch (Exception e) {
        }
    }

    private void closeConnections() {
        try { if (in     != null) in.close();                          } catch (Exception ignored) {}
        try { if (out    != null) out.close();                         } catch (Exception ignored) {}
        try { if (socket != null && !socket.isClosed()) socket.close();} catch (Exception ignored) {}
    }
}