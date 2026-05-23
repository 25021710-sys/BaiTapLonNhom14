package com.auction.client.controller;

import com.auction.client.util.ImageUtil;
import com.auction.common.dto.AuctionDTO;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.function.Consumer;

public class ProductCardController {

    @FXML private VBox rootCard;
    @FXML private Label lblSellerName;
    @FXML private Label lblAuctionId;
    @FXML private ImageView imgProduct;
    @FXML private Label lblNoImage;
    @FXML private Label lblTimeLeft;
    @FXML private Label lblStartPrice;
    @FXML private Label lblCurrentPriceTitle;
    @FXML private Label lblCurrentPrice;
    @FXML private Button btnJoin;

    private AuctionDTO auctionData;
    private Timeline countdown;
    private Consumer<AuctionDTO> onJoinCallback;

    private static final DecimalFormat MONEY = new DecimalFormat("#,###");

    public void setOnJoinCallback(Consumer<AuctionDTO> callback) {
        this.onJoinCallback = callback;
    }

    public void setData(AuctionDTO dto) {
        this.auctionData = dto;

        // Tên sản phẩm ở trên cùng (thay vì tên seller)
        safe(lblSellerName, dto.getItemName() != null ? dto.getItemName() : "Sản phẩm");

        // ID phiên bên dưới
        safe(lblAuctionId, "#" + String.format("%04d", dto.getAuctionId()));

        // Giá khởi điểm
        if (dto.getStartingPrice() != null)
            safe(lblStartPrice, MONEY.format(dto.getStartingPrice()));

        String status = dto.getStatus() != null ? dto.getStatus().name() : "";

        if ("OPEN".equals(status)) {
            safe(lblCurrentPriceTitle, "Thời lượng");

            if (dto.getStartTime() != null && dto.getEndTime() != null) {
                long durationSeconds = java.time.Duration.between(
                    dto.getStartTime(), dto.getEndTime()
                ).getSeconds();

                safe(lblCurrentPrice, formatDurationSmart(durationSeconds));
            } else {
                safe(lblCurrentPrice, "--");
            }

            startCountdownToStart(dto.getStartTime());
        } else {
            safe(lblCurrentPriceTitle, "Hiện tại");
            if (dto.getCurrentPrice() != null)
                safe(lblCurrentPrice, MONEY.format(dto.getCurrentPrice()));
            startCountdownToEnd(dto.getEndTime());
        }

        // Ảnh
        String thumbUrl = dto.getThumbnailUrl();
        if (thumbUrl != null && !thumbUrl.isBlank()) {
            new Thread(() -> {
                Image img = ImageUtil.loadThumbnail(thumbUrl, 240, 160);
                if (img != null) {
                    javafx.application.Platform.runLater(() -> {
                        imgProduct.setImage(img);
                        if (lblNoImage != null) lblNoImage.setVisible(false);
                    });
                }
            }, "load-card-image").start();
        }

        // Nút
        styleJoinButton(status);

        // Hover
        rootCard.setOnMouseEntered(e ->
            rootCard.setStyle(rootCard.getStyle() +
                "-fx-effect: dropshadow(gaussian,rgba(41,128,185,0.45),18,0,0,4);"));
        rootCard.setOnMouseExited(e ->
            rootCard.setStyle(rootCard.getStyle()
                .replace("-fx-effect: dropshadow(gaussian,rgba(41,128,185,0.45),18,0,0,4);", "")));
    }

    @FXML
    public void handleJoin() {
        if (onJoinCallback != null && auctionData != null)
            onJoinCallback.accept(auctionData);
    }

    // ── Countdown đến khi bắt đầu (OPEN) ────────────────────────────────────

    private void startCountdownToStart(LocalDateTime startTime) {
        if (startTime == null || lblTimeLeft == null) return;
        if (countdown != null) countdown.stop();

        countdown = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            long secondsLeft = java.time.Duration.between(
                LocalDateTime.now(), startTime).getSeconds();
            if (secondsLeft <= 0) {
                lblTimeLeft.setText("Đang bắt đầu...");
                countdown.stop();
            } else {
                lblTimeLeft.setText("Bắt đầu trong " + formatTime(secondsLeft));
            }
        }));
        countdown.setCycleCount(Animation.INDEFINITE);
        countdown.play();
    }

    // ── Countdown đến khi kết thúc (RUNNING) ─────────────────────────────────

    private void startCountdownToEnd(LocalDateTime endTime) {
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
                lblTimeLeft.setText("Kết thúc trong " + formatTime(secondsLeft));
            }
        }));
        countdown.setCycleCount(Animation.INDEFINITE);
        countdown.play();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void styleJoinButton(String status) {
        if (btnJoin == null) return;
        switch (status) {
            case "RUNNING" -> {
                btnJoin.setText("Tham gia 🚀");
                btnJoin.setStyle(
                    "-fx-background-color: #2980B9; -fx-text-fill: white;" +
                        "-fx-background-radius: 10; -fx-font-weight: bold; -fx-cursor: hand;");
            }
            case "OPEN" -> {
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

    private String formatTime(long seconds) {
        if (seconds <= 0) return "00:00:00";
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (d > 0) return String.format("%dd %02d:%02d:%02d", d, h, m, s);
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private String formatDurationSmart(long seconds) {
        if (seconds <= 0) return "0s";

        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;

        // Có ngày
        if (d > 0) {
            // tròn ngày
            if (h == 0 && m == 0 && s == 0) {
                return d + "d";
            }
            // tròn giờ
            if (m == 0 && s == 0) {
                return String.format("%dd %02dh", d, h);
            }
            // tròn phút
            if (s == 0) {
                return String.format("%dd %02dh%02dm", d, h, m);
            }
            // không tròn -> giữ full
            return String.format("%dd %02d:%02d:%02d", d, h, m, s);
        }

        // Không có ngày, có giờ
        if (h > 0) {
            // tròn giờ
            if (m == 0 && s == 0) {
                return String.format("%dh", h);
            }
            // tròn phút
            if (s == 0) {
                return String.format("%dh%02dm", h, m);
            }
            // không tròn
            return String.format("%dh%02dm%02ds", h, m, s);
        }

        // Không có ngày, không có giờ, có phút
        if (m > 0) {
            // tròn phút
            if (s == 0) {
                return String.format("%dm", m);
            }
            // không tròn
            return String.format("%dm%02ds", m, s);
        }

        // chỉ có giây
        return String.format("%ds", s);
    }

    private void safe(Label lbl, String text) {
        if (lbl != null) lbl.setText(text);
    }
}

