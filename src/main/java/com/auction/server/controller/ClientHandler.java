package com.auction.server.controller;

import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private final UserController userController = new UserController();
    private final ItemController itemController = new ItemController();
    private final AuctionController auctionController = new AuctionController();

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            // Khởi tạo luồng ghi/đọc
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            System.out.println("Một Client mới đã kết nối: " + socket.getInetAddress());

            while (true) {
                // Đọc Action từ Client gửi lên
                String action = (String) in.readObject();
                System.out.println("Client Yêu cầu: " + action);

                // Dùng tiền tố để phân loại
                String category = action.split("_")[0];
                switch (category) {
                    case "USER":
                        userController.processRequest(action, in, out);
                        break;
                    case "ITEM":
                        itemController.processRequest(action, in, out);
                        break;
                    case "BID":
                    case "AUCTION":
                        auctionController.processRequest(action, in, out);
                        break;
                    default:
                        out.writeObject("ERROR_UNKNOWN_ACTION");
                        out.flush();
                        System.out.println("Lệnh không hợp lệ: " + action);
                }
            }
        } catch (EOFException e) {
            System.out.println("Client đã ngắt kết nối an toàn.");
        } catch (Exception e) {
            System.err.println("Lỗi kết nối với Client: " + e.getMessage());
        } finally {
            closeConnections();
        }
    }

    private void closeConnections() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}