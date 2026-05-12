package com.auction.client.controller;

import com.auction.common.dto.AuctionDTO;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * Controller cho ProductCard.fxml.
 * Nhận AuctionDTO từ DashBoard/JoinedAuction và hiển thị đầy đủ thông tin.
 * Khi bấm "Tham gia" sẽ gọi callback để mở AuctionRoomView.
 */
public class ProductCardController {

    @FXML private VBox rootCard;
    @FXML private Label lblSellerName;
    @FXML private Label lblAuctionId;
    @FXML private StackPane imagePane;
    @FXML private ImageView imgProduct;
    @FXML private Label lblNoImage;
    @FXML private Label lblTimeLeft;
    @FXML private Label lblStartPrice;
    @FXML private Label lblCurrentPrice;
    @FXML private Button btnJoin;

    private AuctionDTO auctionData;
    private Timeline countdown;
    private Consumer<AuctionDTO> onJoinCallback;

    private static final DecimalFormat MONEY = new DecimalFormat("#,###");

    /** DashBoardController / JoinedAuctionController truyền callback này vào */
    public void setOnJoinCallback(Consumer<AuctionDTO> callback) {
        this.onJoinCallback = callback;
    }

    /**
     * Nạp dữ liệu AuctionDTO vào card.
     */
    public void setData(AuctionDTO dto) {
        this.auctionData = dto;

        // Seller / auction ID
        safe(lblSellerName, dto.getSellerName() != null ? dto.getSellerName() : "Người bán");
        safe(lblAuctionId,  "#" + dto.getAuctionId());

        // Giá
        if (dto.getStartingPrice() != null)
            safe(lblStartPrice, MONEY.format(dto.getStartingPrice()));
        if (dto.getCurrentPrice() != null)
            safe(lblCurrentPrice, MONEY.format(dto.getCurrentPrice()));

        // Ảnh sản phẩm
        if (dto.getImageUrl() != null && !dto.getImageUrl().isBlank()) {
            try {
                Image img = new Image(dto.getImageUrl(), true);
                if (imgProduct != null) {
                    imgProduct.setImage(img);
                    if (lblNoImage != null) lblNoImage.setVisible(false);
                }
            } catch (Exception ignored) { /* giữ placeholder */ }
        }

        // Màu nút theo trạng thái
        styleJoinButton(String.valueOf(dto.getStatus()));

        // Countdown
        startCountdown(dto.getEndTime());

        // Hover effect nhẹ
        rootCard.setOnMouseEntered(e ->
                rootCard.setStyle(rootCard.getStyle() +
                        "-fx-effect: dropshadow(gaussian,rgba(41,128,185,0.45),18,0,0,4);"));
        rootCard.setOnMouseExited(e ->
                rootCard.setStyle(rootCard.getStyle()
                        .replace("-fx-effect: dropshadow(gaussian,rgba(41,128,185,0.45),18,0,0,4);", "")));
    }

    @FXML
    public void handleJoin() {
        if (onJoinCallback != null && auctionData != null) {
            onJoinCallback.accept(auctionData);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void styleJoinButton(String status) {
        if (btnJoin == null || status == null) return;
        switch (status.toUpperCase()) {
            case "RUNNING", "ĐANG DIỄN RA" -> {
                btnJoin.setText("Tham gia 🚀");
                btnJoin.setStyle(
                        "-fx-background-color: #2980B9; -fx-text-fill: white;" +
                                "-fx-background-radius: 10; -fx-font-weight: bold; -fx-cursor: hand;");
            }
            case "OPEN", "SẮP DIỄN RA" -> {
                btnJoin.setText("Xem phòng 🔔");
                btnJoin.setStyle(
                        "-fx-background-color: #27AE60; -fx-text-fill: white;" +
                                "-fx-background-radius: 10; -fx-font-weight: bold; -fx-cursor: hand;");
            }
            default -> {
                btnJoin.setText("Đã kết thúc");
                btnJoin.setStyle(
                        "-fx-background-color: #BDC3C7; -fx-text-fill: #7F8C8D;" +
                                "-fx-background-radius: 10; -fx-cursor: default;");
                btnJoin.setDisable(true);
            }
        }
    }

    private void startCountdown(LocalDateTime endTime) {
        if (endTime == null || lblTimeLeft == null) return;
        if (countdown != null) countdown.stop();
        countdown = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            long secondsLeft = java.time.Duration.between(
                    LocalDateTime.now(), endTime).getSeconds();
            if (secondsLeft <= 0) {
                lblTimeLeft.setText("Kết thúc");
                countdown.stop();
                if (btnJoin != null) {
                    btnJoin.setText("Đã kết thúc");
                    btnJoin.setDisable(true);
                }
            } else {
                long h = secondsLeft / 3600;
                long m = (secondsLeft % 3600) / 60;
                long s = secondsLeft % 60;
                lblTimeLeft.setText(String.format(
                        "Kết thúc trong %02d:%02d:%02d", h, m, s));
            }
        }));
        countdown.setCycleCount(Animation.INDEFINITE);
        countdown.play();
    }

    private void safe(Label lbl, String text) {
        if (lbl != null) lbl.setText(text);
    }
}