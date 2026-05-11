package com.auction.client.controller;

import com.auction.common.dto.AuctionDTO;
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

  public void setData(AuctionDTO dto) {
    this.auctionId = dto.getAuctionId();

    lblProductName.setText(dto.getItemName());
    lblAuctionCode.setText(String.format("Mã: AUC-%04d", dto.getAuctionId()));
    lblCurrentPrice.setText(String.format("%,.0f VNĐ",
            dto.getCurrentPrice().doubleValue()));
    lblLeader.setText("Dẫn đầu: " + (
            dto.getHighestBidderUsername() != null
                    && !dto.getHighestBidderUsername().isEmpty()
                    ? dto.getHighestBidderUsername() : "Chưa có"));

    // Màu status
    switch (dto.getStatus()) {
      case "RUNNING", "ĐANG DIỄN RA" -> lblStatus.setStyle(
              "-fx-font-size: 12px; -fx-font-weight: bold;" +
                      "-fx-padding: 6 12; -fx-background-radius: 10;" +
                      "-fx-background-color: #d1fae5; -fx-text-fill: #047857;");
      case "OPEN", "SẮP DIỄN RA" -> lblStatus.setStyle(
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
    startCountdown(dto.getEndTime());
    // Click card
    rootCard.setOnMouseClicked(e -> {
      System.out.println("Open auction: " + auctionId);
      // TODO: chuyển sang AuctionRoom với auctionId
    });
  }

  // Giữ nguyên method cũ cho MyAuctionController
  public void setData(String productName,
                      int auctionId,
                      String status,
                      String currentPrice,
                      String leader,
                      String timeLeft,
                      String imageUrl) {

    this.auctionId = auctionId;

    lblProductName.setText(productName);
    lblAuctionCode.setText("Mã: " + auctionId);
    lblCurrentPrice.setText(currentPrice);
    lblLeader.setText("Dẫn đầu: " + leader);
    lblStatus.setText(status);
    lblTimeLeft.setText(timeLeft);

    try {
      if (imageUrl != null && !imageUrl.isEmpty()) {
        imgProduct.setImage(new Image(imageUrl, true));
      }
    } catch (Exception e) {
      System.out.println("Không load được ảnh: " + imageUrl);
    }

    // Màu status
    switch (status) {
      case "RUNNING" -> lblStatus.setStyle(
              "-fx-font-size: 12px; -fx-font-weight: bold;" +
                      "-fx-padding: 6 12; -fx-background-radius: 10;" +
                      "-fx-background-color: #d1fae5; -fx-text-fill: #047857;");
      case "OPEN" -> lblStatus.setStyle(
              "-fx-font-size: 12px; -fx-font-weight: bold;" +
                      "-fx-padding: 6 12; -fx-background-radius: 10;" +
                      "-fx-background-color: #dbeafe; -fx-text-fill: #1d4ed8;");
      default -> lblStatus.setStyle(
              "-fx-font-size: 12px; -fx-font-weight: bold;" +
                      "-fx-padding: 6 12; -fx-background-radius: 10;" +
                      "-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c;");
    }

    // Click card
    rootCard.setOnMouseClicked(e -> {
      System.out.println("Open auction detail: " + this.auctionId);
      // TODO: chuyển sang AuctionRoom
    });
  }

  private void startCountdown(LocalDateTime endTime) {
    if (endTime == null) return;
    if (countdown != null) countdown.stop();

    countdown = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
      long secondsLeft = java.time.Duration.between(
              LocalDateTime.now(), endTime).getSeconds();

      if (secondsLeft <= 0) {
        lblTimeLeft.setText("Đã kết thúc");
        countdown.stop();
      } else {
        long h = secondsLeft / 3600;
        long m = (secondsLeft % 3600) / 60;
        long s = secondsLeft % 60;
        lblTimeLeft.setText(String.format("%02d:%02d:%02d", h, m, s));
      }
    }));
    countdown.setCycleCount(Animation.INDEFINITE);
    countdown.play();
  }
}

