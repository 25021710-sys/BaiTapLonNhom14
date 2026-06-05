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

    // Kích thước dùng chung cho toàn bộ app
    public static final double WIDTH = 1200;
    public static final double HEIGHT = 700;

    @Override
    public void start(Stage primaryStage) throws Exception {

        // 1. Kết nối đến server
        try {
            SocketClient.getInstance().connect();
        } catch (Exception e) {
            System.err.println("[MainApp] Không thể kết nối server: " + e.getMessage());
        }

        // 2. Load giao diện
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/Welcome.fxml")
        );

        Parent root = loader.load();

        // 3. Tạo scene với kích thước cố định
        Scene scene = new Scene(root, WIDTH, HEIGHT);

        primaryStage.setTitle("Hệ thống đấu giá");
        primaryStage.setScene(scene);

        // Không cho resize
        primaryStage.setResizable(true);

        // Hiển thị giữa màn hình
        primaryStage.centerOnScreen();

        primaryStage.show();

        // 4. Ngắt kết nối khi đóng app
        primaryStage.setOnCloseRequest(
                e -> SocketClient.getInstance().disconnect()
        );
    }

    public static void main(String[] args) {
        launch(args);
    }
}