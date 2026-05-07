package com.auction.client.controller;

import com.auction.session.Session;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
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

public class DashBoardController {

    @FXML
    private Button btnHome;

    @FXML
    private Button btnJoinedAuction;

    @FXML
    private Button btnMyAuction;

    @FXML
    private Button btnCreateAuction;

    @FXML
    private Circle profileCircle;

    @FXML
    private Label lblUsername;

    @FXML
    private ScrollPane spBidsJoined;

    @FXML
    private ScrollPane spFeaturedProducts;

    @FXML
    private ScrollPane spFavoriteProducts;

    @FXML
    private HBox pnlBidsJoined;

    @FXML
    private HBox pnlFeaturedProducts;

    @FXML
    private HBox pnlFavoriteProducts;

    // Buttons section 1
    @FXML
    private Button btnLeftBids;

    @FXML
    private Button btnRightBids;

    // Buttons section 2
    @FXML
    private Button btnLeftFeatured;

    @FXML
    private Button btnRightFeatured;

    // Buttons section 3
    @FXML
    private Button btnLeftFavorite;

    @FXML
    private Button btnRightFavorite;

    @FXML
    private BorderPane rootPane;

    @FXML
    public void initialize() {
        loadProfileImage();
        renderCards();

        // set username từ Session
        if (Session.getCurrentUser() != null) {
            lblUsername.setText(Session.getCurrentUser().getUsername());
        } else {
            lblUsername.setText("Guest");
        }

        setupArrowButtons(spBidsJoined, btnLeftBids, btnRightBids);
        setupArrowButtons(spFeaturedProducts, btnLeftFeatured, btnRightFeatured);
        setupArrowButtons(spFavoriteProducts, btnLeftFavorite, btnRightFavorite);

        setActiveMenu(btnHome);
    }

    private void loadProfileImage() {
        try {
            Image img = new Image(getClass().getResourceAsStream("/image/DSC00245.JPG"));
            if (img != null) {
                profileCircle.setFill(new ImagePattern(img));
            }
        } catch (Exception e) {
            System.err.println("Không tìm thấy ảnh profile, sử dụng màu mặc định.");
        }
    }

    public void renderCards() {
        try {
            renderSection(pnlBidsJoined, 10);
            renderSection(pnlFeaturedProducts, 10);
            renderSection(pnlFavoriteProducts, 10);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void renderSection(HBox panel, int count) throws IOException {
        if (panel == null) {
            System.err.println("Panel is null, kiểm tra fx:id trong FXML!");
            return;
        }

        panel.getChildren().clear();

        for (int i = 0; i < count; i++) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ProductCard.fxml"));
            VBox card = loader.load();
            panel.getChildren().add(card);
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

    // bấm avatar + tên => sang profile
    @FXML
    private void handleGoToProfile(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/UserProfile.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Hồ sơ cá nhân");
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void handleCreateAuctionView() {
        try {

            setActiveMenu(btnCreateAuction);

            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/CreateAuctionView.fxml")
            );

            Parent view = loader.load();

            // chỉ thay CENTER, giữ menu + topbar
            rootPane.setCenter(view);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setActiveMenu(Button activeButton) {

        // remove active tất cả menu
        btnHome.getStyleClass().remove("active-menu");
        btnJoinedAuction.getStyleClass().remove("active-menu");
        btnMyAuction.getStyleClass().remove("active-menu");
        btnCreateAuction.getStyleClass().remove("active-menu");

        // add active cho button hiện tại
        activeButton.getStyleClass().add("active-menu");
    }

    @FXML
    private void handleGoToDashboard() {
        try {
            setActiveMenu(btnHome);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/DashBoardView.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) rootPane.getScene().getWindow(); // rootPane là fx:id BorderPane của bạn
            stage.setScene(new Scene(root));

            stage.setTitle("Dashboard");
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    void handleAuctionList(ActionEvent event) {
        System.out.println("Đã bấm vào danh sách đấu giá!");
    }

}