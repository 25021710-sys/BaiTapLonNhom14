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
 * ClientHandler - xử lý kết nối của MỘT client trên một luồng riêng.
 *
 * Luồng hoạt động:
 *   1. Tạo ObjectOutputStream / ObjectInputStream
 *   2. Vòng lặp đọc action (String) từ client
 *   3. Phân loại theo tiền tố (USER_, ITEM_, BID_, AUCTION_)
 *   4. Chuyển đến controller tương ứng để xử lý tiếp
 *
 * Mỗi ClientHandler giữ một ServerSession riêng, theo dõi
 * user đã đăng nhập trong suốt vòng đời kết nối này.
 */
public class ClientHandler implements Runnable, AuctionObserver {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    /** Session riêng của kết nối này - lưu user đã đăng nhập */
    private final ServerSession session = new ServerSession();

    private final UserController    userController    = new UserController(session);
    private final ItemController    itemController    = new ItemController();
    private final AuctionController auctionController = new AuctionController();

    public ClientHandler(Socket socket) {
        this.socket = socket;
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
                String action = (String) in.readObject();
                log.info("[{}] Yêu cầu: {}", clientAddr, action);

                String prefix = action.contains("_") ? action.split("_")[0] : action;
                switch (prefix) {
                    case "USER"    -> userController.processRequest(action, in, out);
                    case "ITEM"    -> itemController.processRequest(action, in, out);
                    case "BID", "AUCTION", "AUTOBID" ->
                            auctionController.processRequest(action, in, out, this);
                    default -> {
                        log.warn("[{}] Action không xác định: {}", clientAddr, action);
                        out.writeObject("ERROR_UNKNOWN_ACTION");
                        out.flush();
                    }
                }
            }

        } catch (EOFException e) {
            log.info("[{}] Client ngắt kết nối. ({})", clientAddr, session);
        } catch (Exception e) {
            log.error("[{}] Lỗi kết nối: {} ({})", clientAddr, e.getMessage(), session);
        } finally {
            session.logout();
            closeConnections();
        }
    }

    /**
     * Observer callback: được gọi bởi AuctionManager khi có update.
     * Push AuctionUpdateDTO về client qua socket.
     * synchronized để tránh nhiều thread cùng ghi vào out stream.
     */
    @Override
    public synchronized void onAuctionUpdate(AuctionUpdateDTO update) {
        try {
            if (out != null && !socket.isClosed()) {
                out.writeObject("AUCTION_PUSH_UPDATE");
                out.writeObject(update);
                out.flush();
                out.reset();
            }
        } catch (Exception e) {
            log.warn("Không thể push update đến client: {}", e.getMessage());
        }
    }

    /** Push raw JSON notification (backward compat) */
    public synchronized void sendNotification(String jsonPayload) throws Exception {
        if (out != null && !socket.isClosed()) {
            out.writeObject("PUSH_NOTIFICATION");
            out.writeObject(jsonPayload);
            out.flush();
        }
    }

    private void closeConnections() {
        try { if (in     != null) in.close();                          } catch (Exception ignored) {}
        try { if (out    != null) out.close();                         } catch (Exception ignored) {}
        try { if (socket != null && !socket.isClosed()) socket.close();} catch (Exception ignored) {}
    }
}