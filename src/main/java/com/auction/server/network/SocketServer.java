package com.auction.server.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * SocketServer - Lắng nghe kết nối từ client và tạo luồng xử lý cho mỗi client.
 *
 * Luồng hoạt động:
 *   SocketServer.start()
 *       └─ accept() → ClientHandler(socket) → new Thread → run()
 *                          └─ đọc action → UserController / ItemController / AuctionController
 */
public class SocketServer {

    private static final Logger log = LoggerFactory.getLogger(SocketServer.class);

    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_CLIENTS  = 100;

    private final int port;
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    // Thread pool giới hạn số client đồng thời
    private final ExecutorService threadPool =
            Executors.newFixedThreadPool(MAX_CLIENTS);

    // ── Constructor ───────────────────────────────────────────

    public SocketServer() {
        this(DEFAULT_PORT);
    }

    public SocketServer(int port) {
        this.port = port;
    }

    // ── Lifecycle ─────────────────────────────────────────────

    /**
     * Khởi động server — chặn luồng hiện tại cho đến khi stop() được gọi.
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            log.info("=== Server đang chạy trên cổng {} (tối đa {} client) ===", port, MAX_CLIENTS);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept(); // chờ client kết nối
                    log.info("Client mới kết nối: {}", clientSocket.getInetAddress());

                    // Tạo handler và chạy trên thread pool
                    ClientHandler handler = new ClientHandler(clientSocket);
                    threadPool.submit(handler);

                } catch (IOException e) {
                    if (running) {
                        log.error("Lỗi khi accept kết nối mới", e);
                    }
                    // Nếu !running thì server đang dừng — bỏ qua lỗi
                }
            }
        } catch (IOException e) {
            log.error("Không thể khởi động server trên cổng {}", port, e);
        } finally {
            shutdown();
        }
    }

    /**
     * Dừng server an toàn.
     */
    public void stop() {
        log.info("Đang dừng server...");
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close(); // làm serverSocket.accept() ném IOException → thoát vòng lặp
            }
        } catch (IOException e) {
            log.error("Lỗi khi đóng ServerSocket", e);
        }
    }

    private void shutdown() {
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Server đã dừng hoàn toàn.");
    }

    // ── Entry Point ───────────────────────────────────────────

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        SocketServer server = new SocketServer(port);

        // Hook dừng khi nhấn Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "shutdown-hook"));

        server.start(); // chặn ở đây
    }
}
