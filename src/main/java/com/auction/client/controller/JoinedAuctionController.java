package com.auction.client.controller;

import com.auction.client.network.SocketClient;
import com.auction.client.session.ClientSession;
import com.auction.common.dto.AuctionDTO;
import com.auction.common.response.AuctionListResponse;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Controller màn hình "Phiên đấu giá đang tham gia".
 * Load danh sách phiên từ server, lọc theo trạng thái,
 * và cho phép mở AuctionRoom từng phiên.
 */
public class JoinedAuctionController {

  @FXML private ComboBox<String> cbStatus;
  @FXML private VBox auctionListContainer;
  @FXML private Label lblEmpty;

  /** Callback truyền từ DashBoardController để mở phòng đấu giá */
  private Consumer<AuctionDTO> openRoomCallback;
  private List<AuctionDTO> allAuctions;

  public void setOpenRoomCallback(Consumer<AuctionDTO> callback) {
    this.openRoomCallback = callback;
  }

  @FXML
  public void initialize() {
    cbStatus.getItems().addAll(
            "Tất cả", "Đang diễn ra", "Đã kết thúc", "Bạn thắng", "Bạn thua"
    );
    cbStatus.setValue("Tất cả");
    cbStatus.setOnAction(e -> applyFilter());
    loadJoinedAuctions();
  }

  // ── LOAD DATA TỪ SERVER ───────────────────────────────────────────────────

  private void loadJoinedAuctions() {
    clear();
    showLoading();
    new Thread(() -> {
      try {
        AuctionListResponse res = SocketClient.getInstance().getActiveAuctions();
        if (res != null && res.isSuccess() && res.getAuctions() != null) {
          List<AuctionDTO> all = res.getAuctions();
          // Lọc chỉ các phiên user đã join (dựa vào highestBidderId hoặc participated)
          int myId = ClientSession.getCurrentUser() != null
                  ? ClientSession.getCurrentUser().getId() : -1;
          // Nếu server không trả về danh sách tham gia riêng,
          // tạm thời hiển thị tất cả phiên đang chạy để demo
          allAuctions = all;
          Platform.runLater(this::applyFilter);
        } else {
          Platform.runLater(this::showDemoData);
        }
      } catch (Exception e) {
        System.err.println("Lỗi load joined auctions: " + e.getMessage());
        Platform.runLater(this::showDemoData);
      }
    }, "load-joined-thread").start();
  }

  private void applyFilter() {
    clear();
    if (allAuctions == null || allAuctions.isEmpty()) {
      showEmpty("Bạn chưa tham gia phiên đấu giá nào.");
      return;
    }

    String filter = cbStatus.getValue();
    List<AuctionDTO> filtered;

    switch (filter == null ? "Tất cả" : filter) {
      case "Đang diễn ra" ->
              filtered = allAuctions.stream()
                      .filter(a -> "RUNNING".equalsIgnoreCase(a.getStatus())
                              || "ĐANG DIỄN RA".equalsIgnoreCase(a.getStatus()))
                      .collect(Collectors.toList());
      case "Đã kết thúc" ->
              filtered = allAuctions.stream()
                      .filter(a -> "FINISHED".equalsIgnoreCase(a.getStatus())
                              || "ENDED".equalsIgnoreCase(a.getStatus()))
                      .collect(Collectors.toList());
      case "Bạn thắng" -> {
        int myId = myId();
        filtered = allAuctions.stream()
                .filter(a -> a.getHighestBidderId() == myId
                        && ("FINISHED".equalsIgnoreCase(a.getStatus())
                        || "ENDED".equalsIgnoreCase(a.getStatus())))
                .collect(Collectors.toList());
      }
      case "Bạn thua" -> {
        int myId = myId();
        filtered = allAuctions.stream()
                .filter(a -> a.getHighestBidderId() != myId
                        && ("FINISHED".equalsIgnoreCase(a.getStatus())
                        || "ENDED".equalsIgnoreCase(a.getStatus())))
                .collect(Collectors.toList());
      }
      default -> filtered = allAuctions;
    }

    if (filtered.isEmpty()) {
      showEmpty("Không có phiên nào trong danh mục này.");
      return;
    }

    for (AuctionDTO dto : filtered) {
      addAuctionCard(dto);
    }
  }

  /** Hiển thị demo data khi không có kết nối server */
  private void showDemoData() {
    allAuctions = List.of(); // danh sách rỗng
    showEmpty("Không thể kết nối server. Vui lòng thử lại.");
  }

  // ── RENDER ────────────────────────────────────────────────────────────────

  private void addAuctionCard(AuctionDTO dto) {
    try {
      FXMLLoader loader = new FXMLLoader(
              getClass().getResource("/view/AuctionCard.fxml"));
      Parent cardNode = loader.load();
      AuctionCardController ctrl = loader.getController();
      ctrl.setData(dto);
      ctrl.setOnJoinCallback(this::handleOpenRoom);
      auctionListContainer.getChildren().add(cardNode);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void handleOpenRoom(AuctionDTO dto) {
    if (openRoomCallback != null) openRoomCallback.accept(dto);
  }

  // ── UI HELPERS ────────────────────────────────────────────────────────────

  private void clear() {
    auctionListContainer.getChildren().clear();
  }

  private void showLoading() {
    Label loading = new Label("Đang tải dữ liệu...");
    loading.setStyle("-fx-text-fill: #78909C; -fx-font-size: 14px; -fx-padding: 20;");
    auctionListContainer.getChildren().add(loading);
  }

  private void showEmpty(String msg) {
    Label empty = new Label(msg);
    empty.setStyle("-fx-text-fill: #90A4AE; -fx-font-size: 14px; -fx-padding: 30;");
    auctionListContainer.getChildren().add(empty);
  }

  private int myId() {
    return ClientSession.getCurrentUser() != null
            ? ClientSession.getCurrentUser().getId() : -1;
  }

  @FXML
  private void handleRefresh(ActionEvent event) {
    allAuctions = null;
    loadJoinedAuctions();
  }
}