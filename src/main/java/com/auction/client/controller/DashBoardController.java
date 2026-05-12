package com.auction.client.controller;

import com.auction.client.network.SocketClient;
import com.auction.client.session.ClientSession;
import com.auction.common.dto.AuctionDTO;
import com.auction.common.dto.UserDTO;
import com.auction.common.response.AuctionListResponse;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

public class DashBoardController {

    @FXML private VBox adminSection;
    @FXML private Button btnHome;
    @FXML private Button btnJoinedAuction;
    @FXML private Button btnMyAuction;
    @FXML private Button btnAuctionApproval;
    @FXML private Button btnManageUsers;
    @FXML private Button btnManageRooms;
    @FXML private Button btnCreateAuction;
    @FXML private Circle profileCircle;
    @FXML private Label lblUsername;
    @FXML private ScrollPane spOpenBids;
    @FXML private ScrollPane spUpComingBids;
    @FXML private HBox pnlOpenBids;
    @FXML private HBox pnlUpComingBids;
    @FXML private Button btnLeftOpenBids;
    @FXML private Button btnRightOpenBids;
    @FXML private Button btnLeftUpComingBids;
    @FXML private Button btnRightUpComingBids;
    @FXML private BorderPane rootPane;

    private Parent adminApprovalView;
    private AdminAuctionApprovalController adminApprovalController;

    // ── INIT ──────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        loadProfileImage();

        if (ClientSession.getCurrentUser() != null) {
            UserDTO u = ClientSession.getCurrentUser();
            lblUsername.setText(u.getUsername());
            boolean isAdmin = "ADMIN".equals(u.getRole());
            adminSection.setVisible(isAdmin);
            adminSection.setManaged(isAdmin);
        } else {
            lblUsername.setText("Guest");
            adminSection.setVisible(false);
            adminSection.setManaged(false);
        }

        setupArrowButtons(spOpenBids,       btnLeftOpenBids,     btnRightOpenBids);
        setupArrowButtons(spUpComingBids, btnLeftUpComingBids, btnRightUpComingBids);
        setActiveMenu(btnHome);

        // Load data thật từ server (background thread)
        loadAuctionDataFromServer();
    }

    // ── LOAD DATA TỪ SERVER ───────────────────────────────────────────────────

    private void loadAuctionDataFromServer() {
        new Thread(() -> {
            try {
                AuctionListResponse response = SocketClient.getInstance().getDashboardAuctions();
                if (response != null && response.isSuccess() && response.getAuctions() != null) {
                    Platform.runLater(() -> renderCardsFromData(response.getAuctions()));
                } else {
                    Platform.runLater(this::renderPlaceholderCards);
                }
            } catch (Exception e) {
                System.err.println("Lỗi load auctions: " + e.getMessage());
                Platform.runLater(this::renderPlaceholderCards);
            }
        }, "load-auctions-thread").start();
    }

    /** Render card với AuctionDTO thật – hiển thị tất cả auctions ở cả 2 section */
    private void renderCardsFromData(List<AuctionDTO> auctions) {
        if (pnlOpenBids    != null) pnlOpenBids.getChildren().clear();
        if (pnlUpComingBids != null) pnlUpComingBids.getChildren().clear();

        for (AuctionDTO dto : auctions) {
            String status = dto.getStatus() != null ? dto.getStatus().name() : "";
            switch (status) {
                case "RUNNING" -> addAuctionCard(pnlOpenBids, dto);
                case "OPEN"    -> addAuctionCard(pnlUpComingBids, dto);
            }
        }
    }

    private void addProductCard(HBox panel, AuctionDTO dto) {
        addAuctionCard(panel, dto);
    }

    // SAU
    private void addAuctionCard(HBox panel, AuctionDTO dto) {
        if (panel == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/ProductCard.fxml"));
            Node card = loader.load();
            ProductCardController ctrl = loader.getController();
            ctrl.setData(dto);
            ctrl.setOnJoinCallback(this::openAuctionRoom);
            panel.getChildren().add(card);
        } catch (IOException e) {
            System.err.println("Lỗi load ProductCard: " + e.getMessage());
        }
    }



    // SAU — để trống thay vì hiện placeholder
    private void renderPlaceholderCards() {
        if (pnlOpenBids    != null) pnlOpenBids.getChildren().clear();
        if (pnlUpComingBids != null) pnlUpComingBids.getChildren().clear();
    }



    private void renderSection(HBox panel, int count) throws IOException {
        if (panel == null) return;
        panel.getChildren().clear();
        for (int i = 0; i < count; i++) {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/ProductCard.fxml"));
            panel.getChildren().add(loader.load());
        }
    }

    // ── MỞ PHÒNG ĐẤU GIÁ ────────────────────────────────────────────────────

    /**
     * Mở AuctionRoomView trong CENTER của BorderPane.
     * Được gọi bởi ProductCardController / AuctionCardController qua callback.
     */
    // SAU
    public void openAuctionRoom(AuctionDTO dto) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/AuctionRoomView.fxml"));
            Parent roomView = loader.load();
            AuctionRoomController roomCtrl = loader.getController();
            roomCtrl.loadAuction(dto);

            Stage stage = new Stage();
            stage.setTitle("Phiên đấu giá: " + dto.getItemName());
            stage.setScene(new Scene(roomView));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR,
                "Không thể mở phòng đấu giá:\n" + e.getMessage());
            alert.setHeaderText("Lỗi");
            alert.showAndWait();
        }
    }

    // ── NAVIGATION ───────────────────────────────────────────────────────────

    private void loadProfileImage() {
        try {
            Image img = new Image(getClass().getResourceAsStream("/image/DSC00245.JPG"));
            if (img != null && profileCircle != null)
                profileCircle.setFill(new ImagePattern(img));
        } catch (Exception e) {
            System.err.println("Không tìm thấy ảnh profile.");
        }
    }

    private void setupArrowButtons(ScrollPane sp, Button leftBtn, Button rightBtn) {
        if (sp == null) return;
        double step = 0.25;
        if (leftBtn  != null) leftBtn.setOnAction(e  -> sp.setHvalue(Math.max(0, sp.getHvalue() - step)));
        if (rightBtn != null) rightBtn.setOnAction(e -> sp.setHvalue(Math.min(1, sp.getHvalue() + step)));
    }

    private void setActiveMenu(Button activeButton) {
        for (Button b : new Button[]{btnHome, btnJoinedAuction, btnMyAuction, btnCreateAuction}) {
            if (b != null) b.getStyleClass().remove("active-menu");
        }
        if (activeButton != null) activeButton.getStyleClass().add("active-menu");
    }

    private void clear(HBox panel) {
        if (panel != null) panel.getChildren().clear();
    }

    @FXML
    private void handleGoToProfile(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/UserProfile.fxml"));
            Stage stage = (Stage)((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(loader.load()));
            stage.setTitle("Hồ sơ cá nhân");
            stage.show();
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML
    public void handleCreateAuctionView() {
        try {
            setActiveMenu(btnCreateAuction);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/CreateAuctionView.fxml"));
            rootPane.setCenter(loader.load());
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML
    private void handleGoToDashboard() {
        try {
            setActiveMenu(btnHome);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/DashBoardView.fxml"));
            Stage stage = (Stage) rootPane.getScene().getWindow();
            stage.setScene(new Scene(loader.load()));
            stage.setTitle("Dashboard");
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    public void handleAuctionApproval(ActionEvent event) {
        try {
            setActiveMenu(btnAuctionApproval);
            if (adminApprovalView == null) {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/view/AdminAuctionApprovalView.fxml"));
                adminApprovalView = loader.load();
                adminApprovalController = loader.getController();
            } else {
                adminApprovalController.handleRefresh();
            }
            rootPane.setCenter(adminApprovalView);
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML
    public void handleManageRooms(ActionEvent event) {
        try {
            setActiveMenu(btnManageRooms);
            rootPane.setCenter(new FXMLLoader(
                    getClass().getResource("/view/AdminRoomManagementView.fxml")).load());
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML
    public void handleManageUsers(ActionEvent event) {
        try {
            setActiveMenu(btnManageUsers);
            rootPane.setCenter(new FXMLLoader(
                    getClass().getResource("/view/AdminUserManagementView.fxml")).load());
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML
    public void handleJoinedAuctionView() {
        try {
            setActiveMenu(btnJoinedAuction);
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/JoinedAuctionView.fxml"));
            Parent view = loader.load();
            JoinedAuctionController ctrl = loader.getController();
            ctrl.setOpenRoomCallback(this::openAuctionRoom);
            rootPane.setCenter(view);
        } catch (IOException e) {
            System.out.println("Lỗi load JoinedAuction: " + e.getMessage());
        }
    }

    // SAU
    @FXML
    public void handleMyAuctionView() {
        try {
            setActiveMenu(btnMyAuction);
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/MyAuctionView.fxml"));
            Parent view = loader.load();
            MyAuctionController ctrl = loader.getController();
            ctrl.setOpenRoomCallback(this::openAuctionRoom);
            rootPane.setCenter(view);
        } catch (IOException e) {
            System.out.println("Lỗi load MyAuction: " + e.getMessage());
        }
    }
}