package com.auction.client.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import com.auction.client.session.ClientSession;
import com.auction.common.dto.UserDTO;

import java.io.IOException;

public class UserProfileController {
    @FXML private Label sideBarName;
    @FXML private Label sideBarEmail;
    @FXML private ImageView HistoryLogo;
    @FXML private ImageView userAvatar;
    @FXML private ImageView userLogo;
    @FXML private ImageView BalanceLogo;
    @FXML private ImageView LogoutLogo;
    @FXML private ImageView HomeLogo;
    @FXML private AnchorPane contentArea;
    @FXML private ImageView sideBarAvatar;

    private void setPage(String fxmlPath) {
        try {
            // 1. Load file giao diện mới
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/" + fxmlPath));
            Node node = loader.load();

            // 2. Xóa nội dung hiện tại và thêm nội dung mới vào
            contentArea.getChildren().clear();
            contentArea.getChildren().add(node);
            AnchorPane.setTopAnchor(node, 0.0);
            AnchorPane.setBottomAnchor(node, 0.0);
            AnchorPane.setLeftAnchor(node, 0.0);
            AnchorPane.setRightAnchor(node, 0.0);

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
    public void showBalance() {
        setPage("BalanceView.fxml");
    }

    @FXML
    public void handleLogout(javafx.scene.input.MouseEvent event) {

        try {

            ClientSession.clear();

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/LoginView.fxml")
            );

            Parent root = loader.load();

            Stage stage = (Stage) ((Node) event.getSource())
                    .getScene()
                    .getWindow();

            // Giữ nguyên kích thước hiện tại
            double width = stage.getWidth();
            double height = stage.getHeight();

            Scene scene = new Scene(root, width, height);

            stage.setScene(scene);

            stage.setTitle("Login");

            stage.centerOnScreen();

            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void returnDashBoard(javafx.scene.input.MouseEvent event) {

        try {

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/DashboardView.fxml")
            );

            Parent root = loader.load();

            Stage stage = (Stage) ((Node) event.getSource())
                    .getScene()
                    .getWindow();

            // Giữ nguyên kích thước hiện tại
            double width = stage.getWidth();
            double height = stage.getHeight();

            Scene scene = new Scene(root, width, height);

            stage.setScene(scene);
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @FXML
    public void initialize() {
        UserDTO user = ClientSession.getCurrentUser();

        if (user != null) {
            sideBarName.setText(user.getUsername());
            sideBarEmail.setText(user.getEmail());
        } else {
            sideBarName.setText("Guest");
            sideBarEmail.setText("");
        }
    }
}
