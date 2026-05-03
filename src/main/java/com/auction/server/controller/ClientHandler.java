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
    private final AuctionController auctionController = new AuctionController();
    private final ItemController itemController = new ItemController();

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            while (true) {
                // Đọc lệnh từ Client, vd: "USER_LOGIN"
                String action = (String) in.readObject();
                String category = action.split("_")[0];

                switch (category) {
                    case "USER":
                        userController.processRequest(action, in, out);
                        break;
                    case "AUCTION":
                    case "AUTOBID":
                    case "BID":
                        auctionController.processRequest(action, in, out);
                        break;
                    case "ITEM":
                        itemController.processRequest(action, in, out);
                        break;
                    default:
                        out.writeObject("ERROR: Unknown Action");
                        out.flush();
                }
            }
        } catch (EOFException e) {
            System.out.println("Client ngắt kết nối.");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { if (in != null) in.close(); if (out != null) out.close(); socket.close(); } catch (Exception ignored) {}
        }
    }
}