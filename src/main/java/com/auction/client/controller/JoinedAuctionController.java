package com.auction.client.controller;

import com.auction.client.network.SocketClient;
import com.auction.common.dto.AuctionDTO;
import com.auction.common.response.AuctionListResponse;
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

    new Thread(() -> {
      AuctionListResponse response =
              SocketClient.getInstance().getActiveAuctions();

      javafx.application.Platform.runLater(() -> {
        if (response == null || response.getAuctions() == null
                || response.getAuctions().isEmpty()) return;

        for (AuctionDTO dto : response.getAuctions()) {
          try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/AuctionCard.fxml"));
            Parent cardNode = loader.load();
            AuctionCardController controller = loader.getController();
            controller.setData(dto); // dùng AuctionDTO
            auctionListContainer.getChildren().add(cardNode);
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      });
    }, "load-joined-thread").start();
  }



  @FXML
  private void handleRefresh(ActionEvent event) {
    loadJoinedAuctions();
  }
}