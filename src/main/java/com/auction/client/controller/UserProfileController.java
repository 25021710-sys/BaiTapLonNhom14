package com.auction.client.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import com.auction.session.Session;
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
    public void showBidHistory() {setPage("BidHistoryView.fxml"); }

    @FXML
    public void handleLogout(javafx.scene.input.MouseEvent event) {
        try {
            Session.clear();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/LoginView.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) ((Node) event.getSource())
                    .getScene().getWindow();

            stage.setScene(new Scene(root));
            stage.setTitle("Login"); // optional
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void returnDashBoard(javafx.scene.input.MouseEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/DashboardView.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) ((Node) event.getSource())
                    .getScene().getWindow();

            stage.setScene(new Scene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @FXML
    public void initialize() {
        UserDTO user = Session.getCurrentUser();

        if (user != null) {
            sideBarName.setText(user.getUsername());
            sideBarEmail.setText(user.getEmail());
        } else {
            sideBarName.setText("Guest");
            sideBarEmail.setText("");
        }
    }
}
