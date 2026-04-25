package com.auction.client.controller;

import javafx.event.ActionEvent; // Sửa import này
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.image.Image;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import java.io.IOException;

public class DashBoardController {

    @FXML
    private Circle profileCircle;

    @FXML
    private FlowPane pnlItems;

    @FXML
    public void initialize() {
        try {
            // 1. Tải ảnh từ resources (Đảm bảo file DSC00245.JPG đã nằm trong folder resources/image)
            Image img = new Image(getClass().getResourceAsStream("/image/DSC00245.JPG"));
            if (img != null) {
                profileCircle.setFill(new ImagePattern(img));
            }
        } catch (Exception e) {
            System.err.println("Không tìm thấy ảnh profile, sử dụng màu mặc định.");
        }

        renderCards();
    }

    public void renderCards() {
        try {
            if (pnlItems != null) {
                pnlItems.getChildren().clear();
                for (int i = 0; i < 5; i++) {
                    FXMLLoader loader = new FXMLLoader();
                    loader.setLocation(getClass().getResource("/view/ProductCard.fxml"));
                    VBox card = loader.load();
                    pnlItems.getChildren().add(card);
                }
            }
        } catch (IOException e) {
            System.err.println("Lỗi nạp ProductCard: " + e.getMessage());
        }
    }

    @FXML
    void handleAuctionList(ActionEvent event) {
        System.out.println("Đã bấm vào danh sách đấu giá!");
    }
}