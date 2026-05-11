package com.auction.client.controller;

import com.auction.client.network.SocketClient;
import com.auction.common.dto.AuctionDTO;
import com.auction.common.response.AuctionListResponse;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

public class MyAuctionController {

  @FXML private ComboBox<String> cbStatus;
  @FXML private VBox auctionListContainer;

  @FXML
  public void initialize() {
    cbStatus.getItems().addAll("Tat ca", "Dang dien ra", "Da ket thuc");
    cbStatus.setValue("Tat ca");
    cbStatus.setOnAction(e -> loadMyAuction());
    loadMyAuction();
  }

  private void loadMyAuction() {
    auctionListContainer.getChildren().clear();

    new Thread(() -> {
      AuctionListResponse response = SocketClient.getInstance().getMyAuctions();

      Platform.runLater(() -> {
        if (response == null || !response.isSuccess() || response.getAuctions() == null) {
          System.out.println("Khong lay duoc danh sach: "
              + (response != null ? response.getMessage() : "null"));
          return;
        }

        String filter = cbStatus.getValue();

        for (AuctionDTO dto : response.getAuctions()) {
          boolean isRunning = "OPEN".equals(dto.getStatus())
              || "RUNNING".equals(dto.getStatus());

          if ("Dang dien ra".equals(filter) && !isRunning) continue;
          if ("Da ket thuc".equals(filter) && isRunning) continue;

          addAuctionCard(dto);
        }
      });
    }, "load-my-auctions-thread").start();
  }

  private void addAuctionCard(AuctionDTO dto) {
    try {
      FXMLLoader loader = new FXMLLoader(
          getClass().getResource("/view/AuctionCard.fxml"));
      Parent cardNode = loader.load();

      AuctionCardController controller = loader.getController();

      boolean isRunning = "OPEN".equals(dto.getStatus())
          || "RUNNING".equals(dto.getStatus());
      String statusLabel = isRunning ? "DANG DIEN RA" : dto.getStatus();
      String timeLeft    = formatTimeLeft(dto.getEndTime());
      String imageUrl    = "https://picsum.photos/seed/" + dto.getAuctionId() + "/300/200";
      String price       = dto.getCurrentPrice() != null
          ? dto.getCurrentPrice() + " VND" : "---";
      String leader      = dto.getHighestBidderUsername() != null
          && !dto.getHighestBidderUsername().isEmpty()
          ? dto.getHighestBidderUsername() : "Chua co";

      controller.setData(
          dto.getItemName(),
          "AUC-" + dto.getAuctionId(),
          statusLabel,
          price,
          leader,
          timeLeft,
          imageUrl
      );

      // Truyen DTO de khi click mo duoc AuctionRoom dung thong tin
      controller.setAuctionDTO(dto);

      auctionListContainer.getChildren().add(cardNode);

    } catch (IOException e) {
      System.out.println("Loi load AuctionCard: " + e.getMessage());
    }
  }

  private String formatTimeLeft(LocalDateTime endTime) {
    if (endTime == null) return "--:--:--";
    Duration d = Duration.between(LocalDateTime.now(), endTime);
    if (d.isNegative()) return "Da ket thuc";
    long h = d.toHours();
    long m = d.toMinutesPart();
    long s = d.toSecondsPart();
    return String.format("%02d:%02d:%02d", h, m, s);
  }

  @FXML
  private void handleRefresh(ActionEvent event) {
    loadMyAuction();
  }
}