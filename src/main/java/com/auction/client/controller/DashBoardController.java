package com.auction.client.controller;

import com.auction.common.dto.UserDTO;
import com.auction.session.Session;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;

import java.io.IOException;
import java.io.InputStream;

public class DashBoardController {

    @FXML private VBox adminSection;

    @FXML private Button btnHome;
    @FXML private Button btnJoinedAuction;
    @FXML private Button btnMyAuction;
    @FXML private Button btnCreateAuction;

    @FXML private Button btnAuctionApproval;
    @FXML private Button btnManageUsers;
    @FXML private Button btnManageRooms;

    @FXML private Circle profileCircle;
    @FXML private Label lblUsername;

    @FXML private ScrollPane mainScrollPane;

    @FXML private ScrollPane spBidsJoined;
    @FXML private ScrollPane spFeaturedProducts;
    @FXML private ScrollPane spFavoriteProducts;

    @FXML private HBox pnlBidsJoined;
    @FXML private HBox pnlFeaturedProducts;
    @FXML private HBox pnlFavoriteProducts;

    @FXML private Button btnLeftBids;
    @FXML private Button btnRightBids;

    @FXML private Button btnLeftFeatured;
    @FXML private Button btnRightFeatured;

    @FXML private Button btnLeftFavorite;
    @FXML private Button btnRightFavorite;

    private Node dashboardHomeContent;
    @FXML private VBox contentArea;

    @FXML
    public void initialize() {
        loadProfileImage();
        loadUserInfo();

        // LƯU Ý: Lưu lại toàn bộ mainScrollPane làm Trang chủ, chứ không chỉ content bên trong
        dashboardHomeContent = mainScrollPane;

        setupArrowButtons(spBidsJoined, btnLeftBids, btnRightBids);
        setupArrowButtons(spFeaturedProducts, btnLeftFeatured, btnRightFeatured);
        setupArrowButtons(spFavoriteProducts, btnLeftFavorite, btnRightFavorite);

        renderDashboardCards();
        setActiveMenu(btnHome);
    }

    private void loadUserInfo() {

        if (Session.getCurrentUser() != null) {

            UserDTO currentUser = Session.getCurrentUser();
            lblUsername.setText(currentUser.getUsername());

            boolean isAdmin = "ADMIN".equalsIgnoreCase(currentUser.getRole());
            adminSection.setVisible(isAdmin);
            adminSection.setManaged(isAdmin);

        } else {

            lblUsername.setText("Guest");

            adminSection.setVisible(false);
            adminSection.setManaged(false);
        }
    }

    private void loadProfileImage() {
        try {
            InputStream is = getClass().getResourceAsStream("/image/DSC00245.JPG");
            if (is != null) {
                Image img = new Image(is);
                profileCircle.setFill(new ImagePattern(img));
            }
        } catch (Exception e) {
            System.out.println("Không load được ảnh profile.");
        }
    }

    private void setupArrowButtons(ScrollPane sp, Button leftBtn, Button rightBtn) {

        if (sp == null) return;

        double step = 0.25;

        if (leftBtn != null) {
            leftBtn.setOnAction(e -> sp.setHvalue(Math.max(0, sp.getHvalue() - step)));
        }

        if (rightBtn != null) {
            rightBtn.setOnAction(e -> sp.setHvalue(Math.min(1, sp.getHvalue() + step)));
        }
    }

    private void renderDashboardCards() {
        try {
            renderSection(pnlBidsJoined, 10);
            renderSection(pnlFeaturedProducts, 10);
            renderSection(pnlFavoriteProducts, 10);
        } catch (Exception e) {
            System.out.println("Render cards error: " + e.getMessage());
        }
    }

    private void renderSection(HBox panel, int count) throws IOException {

        if (panel == null) return;

        panel.getChildren().clear();

        for (int i = 0; i < count; i++) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ProductCard.fxml"));
            VBox card = loader.load();
            panel.getChildren().add(card);
        }
    }

    private void loadContent(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();

            // VBox contentArea hiện tại có 2 con: [0] là Top Bar, [1] là màn hình nội dung bên dưới.
            // Xóa nội dung màn hình hiện tại (nếu có) nhưng giữ lại Top Bar (index 0)
            if (contentArea.getChildren().size() > 1) {
                contentArea.getChildren().remove(1);
            }

            // Thêm View mới vào bên dưới Top Bar và cho phép nó chiếm toàn bộ không gian còn lại
            VBox.setVgrow(view, Priority.ALWAYS);
            contentArea.getChildren().add(view);

        } catch (IOException e) {
            System.out.println("Không load được view: " + fxmlPath);
            e.printStackTrace(); // In lỗi ra log để dễ debug
        }
    }

    private void showDashboardHome() {
        if (contentArea.getChildren().size() > 1) {
            contentArea.getChildren().remove(1);
        }
        if (dashboardHomeContent != null) {
            VBox.setVgrow(dashboardHomeContent, Priority.ALWAYS);
            contentArea.getChildren().add(dashboardHomeContent);
        }
    }

    private void setActiveMenu(Button activeButton) {

        btnHome.getStyleClass().remove("active-menu");
        btnJoinedAuction.getStyleClass().remove("active-menu");
        btnMyAuction.getStyleClass().remove("active-menu");
        btnCreateAuction.getStyleClass().remove("active-menu");

        btnAuctionApproval.getStyleClass().remove("active-menu");
        btnManageRooms.getStyleClass().remove("active-menu");
        btnManageUsers.getStyleClass().remove("active-menu");

        if (activeButton != null) {
            activeButton.getStyleClass().add("active-menu");
        }
    }

    // ================== EVENTS ==================

    @FXML
    private void handleGoToProfile(ActionEvent event) {
        System.out.println("Go to profile");
    }

    @FXML
    private void handleGoToDashboard() {
        setActiveMenu(btnHome);
        showDashboardHome();
    }

    @FXML
    public void handleCreateAuctionView() {
        setActiveMenu(btnCreateAuction);
        loadContent("/view/CreateAuctionView.fxml");
    }

    @FXML
    private void handleJoinedAuctionView() {
        setActiveMenu(btnJoinedAuction);
        loadContent("/view/JoinedAuctionView.fxml");
    }

    @FXML
    public void handleAuctionApproval(ActionEvent event) {
        setActiveMenu(btnAuctionApproval);
        loadContent("/view/AdminAuctionApprovalView.fxml");
    }

    @FXML
    public void handleManageRooms(ActionEvent event) {
        setActiveMenu(btnManageRooms);
        loadContent("/view/AdminRoomManagementView.fxml");
    }

    @FXML
    public void handleManageUsers(ActionEvent event) {
        setActiveMenu(btnManageUsers);
        loadContent("/view/AdminUserManagementView.fxml");
    }
}