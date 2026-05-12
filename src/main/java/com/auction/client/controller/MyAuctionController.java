package com.auction.client.controller;

import com.auction.client.network.SocketClient;
import com.auction.common.dto.AuctionDTO;
import com.auction.common.response.AuctionListResponse;
import com.auction.server.model.AuctionStatus;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.function.Consumer;

public class MyAuctionController {

  @FXML
  private ComboBox<String> cbStatus;

  @FXML
  private VBox auctionListContainer;
  private Consumer<AuctionDTO> openRoomCallback;
  public void setOpenRoomCallback(Consumer<AuctionDTO> callback) {
    this.openRoomCallback = callback;
  }

  @FXML
  public void initialize() {

    cbStatus.getItems().addAll(
            "Tất cả",
            "Sắp diễn ra",
            "Đang diễn ra",
            "Đã kết thúc"
    );

    cbStatus.setValue("Tất cả");

    cbStatus.setOnAction(e -> loadMyAuction());

    loadMyAuction();
  }

  private void loadMyAuction() {
    auctionListContainer.getChildren().clear();

    new Thread(() -> {
      try {
        AuctionListResponse response = SocketClient.getInstance().getMyAuctions();

        Platform.runLater(() -> {
          if (!response.isSuccess()) {
            System.out.println("Load auctions failed: " + response.getMessage());
            return;
          }
          if (response.getAuctions() == null || response.getAuctions().isEmpty()) {
            System.out.println("Không có auction nào");
            return;
          }

          String selected = cbStatus.getValue();

          for (AuctionDTO dto : response.getAuctions()) {
            AuctionStatus status = dto.getStatus();
            if (selected != null) {
              switch (selected) {
                case "Sắp diễn ra"  -> { if (status != AuctionStatus.OPEN)     continue; }
                case "Đang diễn ra" -> { if (status != AuctionStatus.RUNNING)  continue; }
                case "Đã kết thúc"  -> { if (status != AuctionStatus.FINISHED) continue; }
              }
            }
            addAuctionCard(dto);
          }
        });

      } catch (Exception e) {
        System.err.println("Load my auctions error: " + e.getMessage());
      }
    }, "load-my-auctions").start();
  }


  private void addAuctionCard(AuctionDTO dto) {
    try {
      FXMLLoader loader = new FXMLLoader(
              getClass().getResource("/view/AuctionCard.fxml")
      );
      Parent cardNode = loader.load();

      AuctionCardController controller = loader.getController();
      controller.setData(dto);
      controller.setOnJoinCallback(this::handleOpenRoom);

      auctionListContainer.getChildren().add(cardNode);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @FXML
  private void handleRefresh(ActionEvent event) {
    loadMyAuction();
  }

  private void handleOpenRoom(AuctionDTO dto) {
    if (openRoomCallback != null) openRoomCallback.accept(dto);
  }
}