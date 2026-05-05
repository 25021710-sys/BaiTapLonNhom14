package com.auction.client;

import com.auction.client.network.SocketClient;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * MainApp - điểm khởi động ứng dụng Client.
 * Kết nối Socket một lần duy nhất khi mở app,
 * ngắt kết nối khi đóng app.
 */
public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // 1. Kết nối đến server
        try {
            SocketClient.getInstance().connect();
        } catch (Exception e) {
            System.err.println("[MainApp] Không thể kết nối server: " + e.getMessage());
            // Vẫn cho phép mở app để hiển thị thông báo lỗi qua UI
        }

        // 2. Load màn hình đăng nhập
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/AuctionRoomView.fxml"));
        Parent root = loader.load();

        primaryStage.setTitle("Hệ thống đấu giá");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();

        // 3. Ngắt kết nối khi đóng app
        primaryStage.setOnCloseRequest(e -> SocketClient.getInstance().disconnect());
    }

    public static void main(String[] args) {
        launch(args);
    }
}