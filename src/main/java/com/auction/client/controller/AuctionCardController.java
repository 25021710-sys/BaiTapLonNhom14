package com.auction.client.controller;

import com.auction.common.dto.AuctionDTO;
import com.auction.server.model.AuctionStatus;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.util.function.Consumer;

public class AuctionCardController {

  @FXML
  private HBox rootCard;
  @FXML
  private ImageView imgProduct;
  @FXML
  private Label lblProductName;
  @FXML
  private Label lblAuctionCode;
  @FXML
  private Label lblCurrentPrice;
  @FXML
  private Label lblLeader;
  @FXML
  private Label lblStatus;
  @FXML
  private Label lblTimeLeft;

  private Timeline countdown;
  private int auctionId;
  private String auctionCode;
  private AuctionDTO currentDto;
  private Consumer<AuctionDTO> onJoinCallback;

  /** DashBoard / JoinedAuction truyền callback để mở AuctionRoom */
  public void setOnJoinCallback(Consumer<AuctionDTO> callback) {
    this.onJoinCallback = callback;
  }

  public void setData(AuctionDTO dto) {
    this.auctionId  = dto.getAuctionId();
    this.currentDto = dto;

    lblProductName.setText(dto.getItemName());
    lblAuctionCode.setText(String.format("Mã: AUC-%04d", dto.getAuctionId()));
    lblCurrentPrice.setText(String.format("%,.0f VNĐ",
            dto.getCurrentPrice().doubleValue()));
    lblLeader.setText("Dẫn đầu: " + (
            dto.getHighestBidderUsername() != null
                    && !dto.getHighestBidderUsername().isEmpty()
                    ? dto.getHighestBidderUsername() : "Chưa có"));
    // =========================
    // STATUS TEXT
    // =========================

    AuctionStatus status = dto.getStatus();

    lblStatus.setText(
            status.getDisplay()
    );
    // Màu status
    switch (dto.getStatus()) {
      case AuctionStatus.RUNNING -> lblStatus.setStyle(
              "-fx-font-size: 12px; -fx-font-weight: bold;" +
                      "-fx-padding: 6 12; -fx-background-radius: 10;" +
                      "-fx-background-color: #d1fae5; -fx-text-fill: #047857;");
      case AuctionStatus.OPEN-> lblStatus.setStyle(
              "-fx-font-size: 12px; -fx-font-weight: bold;" +
                      "-fx-padding: 6 12; -fx-background-radius: 10;" +
                      "-fx-background-color: #dbeafe; -fx-text-fill: #1d4ed8;");
      default -> lblStatus.setStyle(
              "-fx-font-size: 12px; -fx-font-weight: bold;" +
                      "-fx-padding: 6 12; -fx-background-radius: 10;" +
                      "-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c;");
    }
    // Ảnh
    try {
      if (dto.getImageUrl() != null && !dto.getImageUrl().isEmpty()) {
        imgProduct.setImage(new Image(dto.getImageUrl(), true));
      }
    } catch (Exception e) {
      System.out.println("Không load được ảnh");
    }

    // Countdown thật từ endTime
    startCountdown(dto.getStartTime(), dto.getEndTime(), dto.getStatus());
    // Click card → mở AuctionRoom qua callback
    rootCard.setOnMouseClicked(e -> {
      if (onJoinCallback != null && currentDto != null)
        onJoinCallback.accept(currentDto);
    });
  }

  private void startCountdown(
          LocalDateTime startTime,
          LocalDateTime endTime,
          AuctionStatus status
  ) {

    if (endTime == null) {
      return;
    }

    if (countdown != null) {
      countdown.stop();
    }

    countdown = new Timeline(

            new KeyFrame(

                    Duration.seconds(1),

                    e -> {

                      LocalDateTime now =
                              LocalDateTime.now();

                      // =========================
                      // CHƯA BẮT ĐẦU
                      // =========================

                      if (status == AuctionStatus.OPEN
                              &&
                              startTime != null
                              &&
                              now.isBefore(startTime)) {

                        long secondsLeft =
                                java.time.Duration
                                        .between(now, startTime)
                                        .getSeconds();

                        lblTimeLeft.setText(
                                "Bắt đầu sau: "
                                        + formatTime(secondsLeft)
                        );
                      }

                      // =========================
                      // ĐANG / ĐÃ DIỄN RA
                      // =========================

                      else {

                        long secondsLeft =
                                java.time.Duration
                                        .between(now, endTime)
                                        .getSeconds();

                        if (secondsLeft <= 0) {

                          lblTimeLeft.setText(
                                  "Đã kết thúc"
                          );

                          countdown.stop();

                        } else {

                          lblTimeLeft.setText(
                                  formatTime(secondsLeft)
                          );
                        }
                      }
                    }
            )
    );

    countdown.setCycleCount(
            Animation.INDEFINITE
    );

    countdown.play();
  }

  private String formatTime(long secondsLeft) {
    long days = secondsLeft / 86400;
    long hours = (secondsLeft % 86400) / 3600;
    long mins  = (secondsLeft % 3600) / 60;
    long secs  = secondsLeft % 60;

    if (days > 0) {
      return String.format("%dd %02d:%02d:%02d", days, hours, mins, secs);
    } else {
      return String.format("%02d:%02d:%02d", hours, mins, secs);
    }
  }
}