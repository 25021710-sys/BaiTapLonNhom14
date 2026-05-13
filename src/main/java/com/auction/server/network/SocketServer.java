package com.auction.server.network;

import com.auction.server.controller.AuctionController;
import com.auction.server.controller.ItemController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * SocketServer
 *
 * FIX: tạo shared ItemController + AuctionController một lần duy nhất,
 * truyền vào ClientHandler qua constructor thay vì để mỗi ClientHandler
 * tự new() → pendingDtoCache được share đúng cách giữa tất cả client.
 */
public class SocketServer {

    private static final Logger log = LoggerFactory.getLogger(SocketServer.class);

    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_CLIENTS  = 100;

    private final int port;
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    private final ExecutorService threadPool = Executors.newFixedThreadPool(MAX_CLIENTS);

    // Shared controllers — tạo một lần, dùng cho tất cả ClientHandler
    private final ItemController    sharedItemController    = new ItemController();
    private final AuctionController sharedAuctionController = new AuctionController();

    public SocketServer() { this(DEFAULT_PORT); }

    public SocketServer(int port) { this.port = port; }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            log.info("=== Server đang chạy trên cổng {} (tối đa {} client) ===", port, MAX_CLIENTS);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    log.info("Client mới kết nối: {}", clientSocket.getInetAddress());

                    // FIX: truyền shared controller vào ClientHandler
                    ClientHandler handler = new ClientHandler(
                            clientSocket,
                            sharedItemController,
                            sharedAuctionController);
                    threadPool.submit(handler);

                } catch (IOException e) {
                    if (running) log.error("Lỗi khi accept kết nối mới", e);
                }
            }
        } catch (IOException e) {
            log.error("Không thể khởi động server trên cổng {}", port, e);
        } finally {
            shutdown();
        }
    }

    public void stop() {
        log.info("Đang dừng server...");
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (IOException e) {
            log.error("Lỗi khi đóng ServerSocket", e);
        }
    }

    private void shutdown() {
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) threadPool.shutdownNow();
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        com.auction.server.config.DatabaseConnection.close(); // đóng HikariCP pool
        log.info("Server đã dừng hoàn toàn.");
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        SocketServer server = new SocketServer(port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "shutdown-hook"));
        server.start();
    }
}