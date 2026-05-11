package com.auction.client.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;

import java.io.IOException;

public class JoinedAuctionController {

  @FXML
  private ComboBox<String> cbStatus;

  @FXML
  private VBox auctionListContainer;

  @FXML
  public void initialize() {

    cbStatus.getItems().addAll(
        "Tất cả",
        "Đang diễn ra",
        "Đã kết thúc",
        "Bạn thắng",
        "Bạn thua"
    );

    cbStatus.setValue("Tất cả");

    cbStatus.setOnAction(e -> loadJoinedAuctions());

    loadJoinedAuctions();
  }

  private void loadJoinedAuctions() {

    auctionListContainer.getChildren().clear();

    // DEMO DATA (sau này thay bằng data từ server)

    addAuctionCard(
        "Rolex Submariner 2024",
        1,
        "ĐANG DIỄN RA",
        "12,500,000 VNĐ",
        "duyanh",
        "00:12:35",
        "https://i.imgur.com/2nCt3Sbl.jpg"
    );

    addAuctionCard(
        "Macbook Pro M3 Max",
        2,
        "KẾT THÚC",
        "38,000,000 VNĐ",
        "minh123",
        "THUA",
        "https://i.imgur.com/5ZQ0Gzfl.jpg"
    );
  }

  private void addAuctionCard(String productName,
                              int auctionId,
                              String status,
                              String currentPrice,
                              String leader,
                              String timeLeft,
                              String imageUrl) {

    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/AuctionCard.fxml"));
      Parent cardNode = loader.load();

      AuctionCardController controller = loader.getController();
      controller.setData(productName, auctionId, status, currentPrice, leader, timeLeft, imageUrl);

      auctionListContainer.getChildren().add(cardNode);

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @FXML
  private void handleRefresh(ActionEvent event) {
    loadJoinedAuctions();
  }
}