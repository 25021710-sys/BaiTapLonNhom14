package com.auction.client.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;

import java.io.IOException;

public class MyAuctionController {

  @FXML
  private ComboBox<String> cbStatus;

  @FXML
  private VBox auctionListContainer;

  @FXML
  public void initialize() {

    cbStatus.getItems().addAll(
        "Tất cả",
        "Đang diễn ra",
        "Đã kết thúc"
    );

    cbStatus.setValue("Tất cả");

    cbStatus.setOnAction(e -> loadMyAuction());

    loadMyAuction();
  }

  private void loadMyAuction() {

    auctionListContainer.getChildren().clear();

    // DEMO DATA (sau này thay bằng data từ server)

    addAuctionCard(
        "Rolex Submariner 2024",
        "AUC-2026-001",
        "ĐANG DIỄN RA",
        "12,500,000 VNĐ",
        "duyanh",
        "00:12:35",
        "https://i.imgur.com/2nCt3Sbl.jpg"
    );

    addAuctionCard(
        "Macbook Pro M3 Max",
        "AUC-2026-002",
        "KẾT THÚC",
        "38,000,000 VNĐ",
        "minh123",
        "00:00:00",
        "https://i.imgur.com/5ZQ0Gzfl.jpg"
    );
  }

  private void addAuctionCard(String productName,
                              String auctionCode,
                              String status,
                              String currentPrice,
                              String leader,
                              String timeLeft,
                              String imageUrl) {

    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/AuctionCard.fxml"));
      Parent cardNode = loader.load();

      AuctionCardController controller = loader.getController();
      controller.setData(productName, auctionCode, status, currentPrice, leader, timeLeft, imageUrl);

      auctionListContainer.getChildren().add(cardNode);

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @FXML
  private void handleRefresh(ActionEvent event) {
    loadMyAuction();
  }
}