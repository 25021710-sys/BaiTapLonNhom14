package com.auction.server.network;

import com.auction.server.controller.AuctionController;
import com.auction.server.controller.ItemController;
import com.auction.server.controller.UserController;
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
 *   5. Controller đọc request object từ stream và ghi response object trở lại
 */
public class ClientHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    // Controller cho từng nhóm chức năng
    private final UserController userController    = new UserController();
    private final ItemController itemController    = new ItemController();
    private final AuctionController auctionController = new AuctionController();

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        String clientAddr = socket.getInetAddress().getHostAddress();
        try {
            // ObjectOutputStream phải được tạo TRƯỚC ObjectInputStream
            // (tránh deadlock khi cả hai đầu đều chờ header của nhau)
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in  = new ObjectInputStream(socket.getInputStream());

            log.info("[{}] Kết nối thành công.", clientAddr);

            while (true) {
                // Đọc action do client gửi lên
                String action = (String) in.readObject();
                log.info("[{}] Yêu cầu: {}", clientAddr, action);

                // Phân loại theo tiền tố
                String prefix = action.contains("_") ? action.split("_")[0] : action;
                switch (prefix) {
                    case "USER"    -> userController.processRequest(action, in, out);
                    case "ITEM"    -> itemController.processRequest(action, in, out);
                    case "BID",
                         "AUCTION" -> auctionController.processRequest(action, in, out);
                    default -> {
                        log.warn("[{}] Action không xác định: {}", clientAddr, action);
                        out.writeObject("ERROR_UNKNOWN_ACTION");
                        out.flush();
                    }
                }
            }

        } catch (EOFException e) {
            // Client ngắt kết nối bình thường
            log.info("[{}] Client ngắt kết nối.", clientAddr);
        } catch (Exception e) {
            log.error("[{}] Lỗi kết nối: {}", clientAddr, e.getMessage());
        } finally {
            closeConnections();
        }
    }
    /**
     * Push notification realtime đến client này (được gọi từ AuctionManager).
     * Synchronized để tránh conflict với luồng đọc/ghi khác.
     */
    public synchronized void sendNotification(String jsonPayload) throws Exception {
        if (out != null && !socket.isClosed()) {
            // Dùng "PUSH_NOTIFICATION" làm signal để client phân biệt
            out.writeObject("PUSH_NOTIFICATION");
            out.writeObject(jsonPayload);
            out.flush();
        }
    }
    private void closeConnections() {
        try { if (in     != null) in.close();                         } catch (Exception ignored) {}
        try { if (out    != null) out.close();                        } catch (Exception ignored) {}
        try { if (socket != null && !socket.isClosed()) socket.close();} catch (Exception ignored) {}
    }
}