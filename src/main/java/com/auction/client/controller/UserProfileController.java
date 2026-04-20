package com.auction.client.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.Circle;

import java.io.IOException;

public class UserProfileController {
    @FXML
    private ImageView userAvatar;
    @FXML
    private ImageView userLogo;
    @FXML
    private ImageView NotificationLogo;
    @FXML
    private ImageView LogoutLogo;
    @FXML
    private AnchorPane contentArea;
    private void setPage(String fxmlPath) {
        try {
            // 1. Load file giao diện mới
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/" + fxmlPath));
            Node node = loader.load();

            // 2. Xóa nội dung hiện tại và thêm nội dung mới vào
            contentArea.getChildren().clear();
            contentArea.getChildren().add(node);

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Lỗi: Không tìm thấy file " + fxmlPath);
        }
    }
    @FXML
    public void showProfile() {
        setPage("ProfileContent.fxml"); // File FXML chỉ chứa ruột của trang Profile
    }

    @FXML
    public void showNotification() {
        setPage("NotificationView.fxml");
    }

    @FXML
    public void handleLogout() {
        // Code để quay lại màn hình LoginView.fxml
        setPage("LoginView.fxml");
    }
}
