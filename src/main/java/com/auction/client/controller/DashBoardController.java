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
    @FXML private javafx.scene.layout.StackPane sceneRoot;


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
        // ── Lắng nghe push update từ server khi đang ở Dashboard
        SocketClient.getInstance().addPushCallback(this::handleGlobalPushUpdate);
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

            // FIX REALTIME: cleanup khi đóng cửa sổ — unsubscribe server + remove push callback
            // Nếu không có dòng này, callback vẫn còn trong danh sách → nhận push của phòng đã đóng,
            // và participant count trên server không bao giờ giảm.
            stage.setOnHiding(e -> roomCtrl.cleanup());
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

    /**
     * Nhận push update từ server khi user đang ở Dashboard
     * (không ở trong phòng đấu giá cụ thể nào).
     */
    private void handleGlobalPushUpdate(com.auction.common.dto.AuctionUpdateDTO update) {
        if (update.getType() != com.auction.common.dto.AuctionUpdateDTO.UpdateType.AUCTION_ENDED)
            return;

        // Chỉ thông báo nếu user đã từng tham gia phiên này
        // (kiểm tra bằng cách so sánh với danh sách joined auctions — đơn giản hơn
        //  là so winnerId vì user có thể đã bid nhưng không thắng)
        int myId = ClientSession.getCurrentUser() != null
                ? ClientSession.getCurrentUser().getId() : -1;
        if (myId == -1) return;

        int winnerId = update.getHighestBidderId();
        boolean iWon = (winnerId == myId);

        // Chỉ hiện thông báo nếu user liên quan (thắng hoặc thua)
        // Nếu muốn thông báo tất cả mọi phiên thì bỏ điều kiện này
        if (winnerId == 0) return; // không ai thắng → bỏ qua ở Dashboard

        Platform.runLater(() -> showToastNotification(update, iWon, myId));
    }

    /**
     * Hiện toast notification góc dưới phải màn hình.
     * Tự động biến mất sau 6 giây.
     */
    private void showToastNotification(
            com.auction.common.dto.AuctionUpdateDTO update,
            boolean iWon, int myId) {

        int winnerId     = update.getHighestBidderId();
        String winner    = update.getHighestBidderUsername();
        java.math.BigDecimal price = update.getNewPrice();

        // Chỉ hiện nếu user là người thắng hoặc là người thua (đã tham gia)
        // Đơn giản nhất: luôn hiện với mọi phiên kết thúc khi ở Dashboard
        String icon, title, body, bgColor;
        if (iWon) {
            icon    = "🏆";
            title   = "Bạn đã thắng phiên đấu giá!";
            body    = String.format("Giá chốt: %,.0f VNĐ",
                    price != null ? price.doubleValue() : 0);
            bgColor = "#1B5E20";
        } else {
            String winnerName = (winner != null && !winner.isBlank()) ? winner : "Người khác";
            icon    = "🔔";
            title   = "Một phiên đấu giá vừa kết thúc";
            body    = String.format("Người thắng: %s — %,.0f VNĐ",
                    winnerName, price != null ? price.doubleValue() : 0);
            bgColor = "#37474F";
        }

        // ── Build toast UI
        javafx.scene.layout.VBox toast = new javafx.scene.layout.VBox(4);
        toast.setStyle(String.format(
                "-fx-background-color: %s; -fx-background-radius: 12; " +
                        "-fx-padding: 14 18 14 18; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 12, 0, 0, 4);",
                bgColor));
        toast.setMaxWidth(320);

        javafx.scene.control.Label titleLbl = new javafx.scene.control.Label(icon + "  " + title);
        titleLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");
        titleLbl.setWrapText(true);

        javafx.scene.control.Label bodyLbl = new javafx.scene.control.Label(body);
        bodyLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(255,255,255,0.85);");
        bodyLbl.setWrapText(true);

        toast.getChildren().addAll(titleLbl, bodyLbl);

        // ── Đặt toast vào góc dưới phải của rootPane
        javafx.scene.layout.StackPane overlay = new javafx.scene.layout.StackPane(toast);
        overlay.setAlignment(javafx.geometry.Pos.BOTTOM_RIGHT);
        overlay.setStyle("-fx-padding: 0 24 24 0;");
        overlay.setPickOnBounds(false); // không chặn click vào các component bên dưới

        sceneRoot.getChildren().add(overlay);

        // ── Tự động ẩn sau 6 giây với fade out animation
        javafx.animation.FadeTransition fadeOut =
                new javafx.animation.FadeTransition(javafx.util.Duration.seconds(1), overlay);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> sceneRoot.getChildren().remove(overlay));

        javafx.animation.PauseTransition pause =
                new javafx.animation.PauseTransition(javafx.util.Duration.seconds(5));
        pause.setOnFinished(e -> fadeOut.play());
        pause.play();
    }
    /**
     * Gọi khi user logout hoặc đóng Dashboard.
     * Ví dụ: stage.setOnCloseRequest(e -> dashboardController.cleanup());
     */
    public void cleanup() {
        SocketClient.getInstance().removePushCallback(this::handleGlobalPushUpdate);
    }
}