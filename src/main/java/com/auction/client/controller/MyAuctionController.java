package com.auction.client.controller;

import com.auction.client.network.SocketClient;
import com.auction.common.dto.AuctionDTO;
import com.auction.common.response.AuctionListResponse;
import com.auction.server.model.AuctionStatus;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MyAuctionController {

  @FXML private ComboBox<String> cbStatus;
  @FXML private VBox auctionListContainer;

  private Consumer<AuctionDTO> openRoomCallback;

  /** Cache — không gọi server lại khi chỉ đổi filter */
  private List<AuctionDTO> cachedAuctions = null;

  public void setOpenRoomCallback(Consumer<AuctionDTO> callback) {
    this.openRoomCallback = callback;
  }

  @FXML
  public void initialize() {
    cbStatus.getItems().addAll("Tất cả", "Chờ duyệt", "Sắp diễn ra", "Đang diễn ra", "Đã kết thúc");
    cbStatus.setValue("Tất cả");
    // Đổi filter → chỉ render lại từ cache, không gọi server
    cbStatus.setOnAction(e -> renderFromCache());
    loadMyAuction();
  }

  // ── LOAD từ server (chỉ gọi 1 lần hoặc khi Refresh) ─────────────────────

  private void loadMyAuction() {
    showLoading();
    new Thread(() -> {
      try {
        AuctionListResponse response = SocketClient.getInstance().getMyAuctions();
        Platform.runLater(() -> {
          if (response != null && response.isSuccess() && response.getAuctions() != null) {
            cachedAuctions = response.getAuctions();
          } else {
            cachedAuctions = new ArrayList<>();
          }
          renderFromCache();
        });
      } catch (Exception e) {
        System.err.println("Load my auctions error: " + e.getMessage());
        Platform.runLater(() -> {
          cachedAuctions = new ArrayList<>();
          showEmpty("Không thể kết nối server.");
        });
      }
    }, "load-my-auctions").start();
  }

  // ── RENDER từ cache (nhanh, không gọi server) ─────────────────────────────

  private void renderFromCache() {
    if (cachedAuctions == null) { showLoading(); return; }

    // Lọc theo filter hiện tại
    String selected = cbStatus.getValue();
    List<AuctionDTO> filtered = cachedAuctions.stream()
        .filter(dto -> {
          if (selected == null || selected.equals("Tất cả")) return true;
          AuctionStatus s = dto.getStatus();
          return switch (selected) {
            case "Chờ duyệt"    -> s == AuctionStatus.PENDING;
            case "Sắp diễn ra"  -> s == AuctionStatus.OPEN;
            case "Đang diễn ra" -> s == AuctionStatus.RUNNING;
            case "Đã kết thúc"  -> s == AuctionStatus.FINISHED;
            default -> true;
          };
        })
        .collect(Collectors.toList());

    if (filtered.isEmpty()) {
      showEmpty("Không có phiên nào trong danh mục này.");
      return;
    }

    // Load tất cả FXML cards trong background, sau đó add vào UI 1 lần
    final List<AuctionDTO> toRender = filtered;
    new Thread(() -> {
      List<Node> cards = new ArrayList<>();
      for (AuctionDTO dto : toRender) {
        try {
          FXMLLoader loader = new FXMLLoader(
              getClass().getResource("/view/AuctionCard.fxml"));
          Node card = loader.load();
          AuctionCardController ctrl = loader.getController();
          ctrl.setData(dto);
          ctrl.setOnJoinCallback(this::handleOpenRoom);
          cards.add(card);
        } catch (IOException e) {
          System.err.println("Lỗi load AuctionCard: " + e.getMessage());
        }
      }
      // Add tất cả vào UI 1 lần duy nhất → không jank
      Platform.runLater(() -> {
        auctionListContainer.getChildren().setAll(cards);
      });
    }, "render-my-auctions").start();
  }

  // ── UI HELPERS ────────────────────────────────────────────────────────────

  private void showLoading() {
    Label lbl = new Label("Đang tải dữ liệu...");
    lbl.setStyle("-fx-text-fill: #78909C; -fx-font-size: 14px; -fx-padding: 20;");
    auctionListContainer.getChildren().setAll(lbl);
  }

  private void showEmpty(String msg) {
    Label lbl = new Label(msg);
    lbl.setStyle("-fx-text-fill: #90A4AE; -fx-font-size: 14px; -fx-padding: 30;");
    auctionListContainer.getChildren().setAll(lbl);
  }

  private void handleOpenRoom(AuctionDTO dto) {
    if (openRoomCallback != null) openRoomCallback.accept(dto);
  }

  @FXML
  private void handleRefresh(ActionEvent event) {
    cachedAuctions = null; // xóa cache → gọi server lại
    loadMyAuction();
  }

  public void invalidateCache() {
    cachedAuctions = null;
  }

  public void refreshIfVisible() {
    if (cachedAuctions == null) loadMyAuction();
  }
}