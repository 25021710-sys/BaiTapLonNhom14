package com.auction.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

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

  public void setData(String productName,
                      String auctionCode,
                      String status,
                      String currentPrice,
                      String leader,
                      String timeLeft,
                      String imageUrl) {

    this.auctionCode = auctionCode;

    lblProductName.setText(productName);
    lblAuctionCode.setText("Mã: " + auctionCode);
    lblCurrentPrice.setText(currentPrice);
    lblLeader.setText("Dẫn đầu: " + leader);

    lblStatus.setText(status);
    lblTimeLeft.setText(timeLeft);

    try {
      imgProduct.setImage(new Image(imageUrl));
    } catch (Exception e) {
      System.out.println("Không load được ảnh: " + imageUrl);
    }

    // màu status
    if (status.equalsIgnoreCase("ĐANG DIỄN RA")) {
      lblStatus.setStyle(lblStatus.getStyle() +
          "-fx-background-color: #d1fae5;" +
          "-fx-text-fill: #047857;");
    } else {
      lblStatus.setStyle(lblStatus.getStyle() +
          "-fx-background-color: #fee2e2;" +
          "-fx-text-fill: #b91c1c;");
    }

    // click card
    rootCard.setOnMouseClicked(e -> {
      System.out.println("Open auction detail: " + this.auctionCode);

      // TODO: gọi hàm chuyển scene sang AuctionDetail
    });
  }

  public String getAuctionCode() {
    return auctionCode;
  }
}

