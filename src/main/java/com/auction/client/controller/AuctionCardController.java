package com.auction.client.controller;

import com.auction.common.dto.AuctionDTO;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class AuctionCardController {

  @FXML private HBox rootCard;
  @FXML private ImageView imgProduct;
  @FXML private Label lblProductName;
  @FXML private Label lblAuctionCode;
  @FXML private Label lblCurrentPrice;
  @FXML private Label lblLeader;
  @FXML private Label lblStatus;
  @FXML private Label lblTimeLeft;

  private String auctionCode;
  private AuctionDTO auctionDTO;

  public void setData(String productName,
                      String auctionCode,
                      String status,
                      String currentPrice,
                      String leader,
                      String timeLeft,
                      String imageUrl) {

    this.auctionCode = auctionCode;

    lblProductName.setText(productName);
    lblAuctionCode.setText("Ma: " + auctionCode);
    lblCurrentPrice.setText(currentPrice);
    lblLeader.setText("Dan dau: " + leader);
    lblStatus.setText(status);
    lblTimeLeft.setText(timeLeft);

    try {
      imgProduct.setImage(new Image(imageUrl, true));
    } catch (Exception e) {
      System.out.println("Khong load duoc anh: " + imageUrl);
    }

    if (status.equalsIgnoreCase("DANG DIEN RA") || status.equalsIgnoreCase("OPEN")
        || status.equalsIgnoreCase("RUNNING")) {
      lblStatus.setStyle(lblStatus.getStyle()
          + "-fx-background-color: #d1fae5; -fx-text-fill: #047857;");
    } else {
      lblStatus.setStyle(lblStatus.getStyle()
          + "-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c;");
    }

    rootCard.setOnMouseClicked(e -> openAuctionRoom());
  }

  public void setAuctionDTO(AuctionDTO dto) {
    this.auctionDTO = dto;
  }

  public String getAuctionCode() {
    return auctionCode;
  }

  private void openAuctionRoom() {
    if (auctionDTO == null) {
      System.out.println("auctionDTO is null, cannot open room");
      return;
    }
    try {
      FXMLLoader loader = new FXMLLoader(
          getClass().getResource("/view/AuctionRoomView.fxml"));
      Parent view = loader.load();

      AuctionRoomController controller = loader.getController();
      controller.loadAuction(auctionDTO);

      Stage stage = new Stage();
      stage.setTitle("Phien dau gia: " + auctionDTO.getItemName());
      stage.setScene(new Scene(view));
      stage.show();

    } catch (Exception ex) {
      System.out.println("Loi mo AuctionRoom: " + ex.getMessage());
    }
  }
}