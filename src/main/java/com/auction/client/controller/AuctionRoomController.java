package com.auction.client.controller;

import com.auction.client.network.SocketClient;
import com.auction.common.dto.AuctionDTO;
import com.auction.common.dto.AuctionUpdateDTO;
import com.auction.common.request.BidRequest;
import com.auction.common.response.BidHistoryResponse;
import com.auction.common.response.BidResponse;
import com.auction.common.response.CreateAuctionResponse;
import com.auction.server.model.AutoBidConfig;
import com.auction.server.model.BidTransaction;
import com.auction.client.session.ClientSession;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * AuctionRoomController – màn hình đấu giá trực tiếp (realtime bidding).
 */
public class AuctionRoomController {

  // ── TOP INFO ──────────────────────────────────────────────────────────────
  @FXML private Label lblAuctionTitle;
  @FXML private Label lblAuctionStatus;
  @FXML private Label lblCategory;
  @FXML private Label lblStartPrice;
  @FXML private Label lblCurrentPrice;
  @FXML private Label lblTotalBids;
  @FXML private Label lblLeadingUser;
  @FXML private Label lblStepPrice;
  @FXML private TextArea txtDescription;

  // ── IMAGES ────────────────────────────────────────────────────────────────
  @FXML private ImageView imgMainProduct;
  @FXML private ImageView imgThumb1;
  @FXML private ImageView imgThumb2;
  @FXML private ImageView imgThumb3;
  @FXML private ImageView imgThumb4;

  // ── BID HISTORY TABLE ─────────────────────────────────────────────────────
  @FXML private TableView<BidTransaction> tblBidHistory;
  @FXML private TableColumn<BidTransaction, String> colBidTime;
  @FXML private TableColumn<BidTransaction, String> colBidUser;
  @FXML private TableColumn<BidTransaction, String> colBidAmount;
  @FXML private TableColumn<BidTransaction, String> colBidType;
  @FXML private ComboBox<String> cbBidFilter;

  // ── RIGHT PANEL ───────────────────────────────────────────────────────────
  @FXML private Label lblCountdown;
  @FXML private Label lblCurrentPriceRight;
  @FXML private Label lblYourStatus;
  @FXML private TextField txtBidAmount;
  @FXML private Label lblBidError;

  // ── AUTO BID ──────────────────────────────────────────────────────────────
  @FXML private CheckBox chkAutoBid;
  @FXML private TextField txtAutoBidMax;

  // ── SELLER INFO ───────────────────────────────────────────────────────────
  @FXML private Label lblSellerName;
  @FXML private Label lblSellerRating;
  @FXML private Label lblSellerProducts;

  // ── AUCTION INFO ──────────────────────────────────────────────────────────
  @FXML private Label lblAuctionIdTop;
  @FXML private Label lblStartTime;
  @FXML private Label lblEndTime;
  @FXML private Label lblParticipants;

  // ── STATE ─────────────────────────────────────────────────────────────────
  private AuctionDTO currentAuction;
  private BigDecimal currentPrice   = BigDecimal.ZERO;
  private BigDecimal stepPrice      = new BigDecimal("50000");
  private LocalDateTime auctionEndTime;
  private Timeline countdownTimeline;
  private int bidCount = 0;

  private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM");
  private final DecimalFormat     moneyFormat   = new DecimalFormat("#,###");
  private final ObservableList<BidTransaction> bidHistoryList = FXCollections.observableArrayList();

  // ── INIT ──────────────────────────────────────────────────────────────────

  @FXML
  public void initialize() {
    setupBidHistoryTable();
    setupAutoBidToggle();
    setupNumberOnlyFields();
    cbBidFilter.setItems(FXCollections.observableArrayList("Tất cả", "Chỉ của tôi", "Auto-bid"));
    cbBidFilter.setValue("Tất cả");
    lblBidError.setVisible(false);
  }

  /**
   * Được gọi từ DashboardController khi user click vào 1 phiên đấu giá.
   */
  public void loadAuction(AuctionDTO auction) {
    this.currentAuction  = auction;
    this.currentPrice    = auction.getCurrentPrice() != null
        ? auction.getCurrentPrice() : auction.getStartingPrice();
    this.auctionEndTime  = auction.getEndTime();

    Platform.runLater(() -> {
      lblAuctionTitle.setText("Đấu giá: " + auction.getItemName());
      lblAuctionStatus.setText(auction.getStatus());
      lblCategory.setText(auction.getItemCategory());
      lblStartPrice.setText(formatMoney(auction.getStartingPrice()) + " VNĐ");
      lblStepPrice.setText(formatMoney(stepPrice) + " VNĐ");
      lblTotalBids.setText(String.valueOf(auction.getTotalBids()));
      lblLeadingUser.setText(auction.getHighestBidderUsername() != null
          ? auction.getHighestBidderUsername() : "---");
      if (auction.getItemDescription() != null)
        txtDescription.setText(auction.getItemDescription());
      if (lblAuctionIdTop != null)
        lblAuctionIdTop.setText("#AUC" + String.format("%05d", auction.getAuctionId()));
      if (lblStartTime != null)
        lblStartTime.setText("Bắt đầu: " + auction.getStartTime().format(timeFormatter));
      if (lblEndTime != null)
        lblEndTime.setText("Kết thúc: " + auction.getEndTime().format(timeFormatter));
      if (lblSellerName != null)
        lblSellerName.setText(auction.getSellerName());
      updateCurrentPriceUI();
      updateYourStatus();
    });

    subscribeRealtime(auction.getAuctionId());
    loadBidHistory();
    startCountdownTimer();
  }

  // ── SUBSCRIBE REALTIME ────────────────────────────────────────────────────

  private void subscribeRealtime(int auctionId) {
    SocketClient.getInstance().setPushCallback(this::handlePushUpdate);

    new Thread(() -> {
      CreateAuctionResponse res = SocketClient.getInstance().subscribeAuction(auctionId);
      if (!res.isSuccess()) {
        Platform.runLater(() -> showBidError("Lỗi kết nối phiên: " + res.getMessage()));
      }
    }, "subscribe-thread").start();
  }

  private void handlePushUpdate(AuctionUpdateDTO update) {
    if (currentAuction == null || update.getAuctionId() != currentAuction.getAuctionId()) return;

    Platform.runLater(() -> {
      switch (update.getType()) {
        case BID_PLACED, AUCTION_EXTENDED -> {
          // FIX 1: dùng getNewPrice() không phải getAmount() — field đúng trong AuctionUpdateDTO
          if (update.getNewPrice() != null) currentPrice = update.getNewPrice();
          if (update.getNewEndTime() != null) auctionEndTime = update.getNewEndTime();
          bidCount++;
          updateCurrentPriceUI();
          lblTotalBids.setText(String.valueOf(bidCount));
          if (update.getHighestBidderUsername() != null)
            lblLeadingUser.setText(update.getHighestBidderUsername());
          // FIX 2: cập nhật highestBidderId trong currentAuction để updateYourStatus() hoạt động
          currentAuction.setHighestBidderId(update.getHighestBidderId());
          updateYourStatus();
          if (update.getType() == AuctionUpdateDTO.UpdateType.AUCTION_EXTENDED) {
            showBidError("⏱ Phiên được gia hạn thêm 60 giây!");
          }
          loadBidHistory();
        }
        case AUCTION_ENDED -> {
          lblAuctionStatus.setText("ĐÃ KẾT THÚC");
          lblAuctionStatus.setStyle(
              "-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c; " +
                  "-fx-padding: 3 10; -fx-background-radius: 12; -fx-font-size: 11px; -fx-font-weight: bold;");
          stopCountdown();
          disableBidActions();
        }
        default -> {}
      }
    });
  }

  // ── LOAD BID HISTORY ──────────────────────────────────────────────────────

  private void loadBidHistory() {
    if (currentAuction == null) return;
    new Thread(() -> {
      BidHistoryResponse res = SocketClient.getInstance().getBidHistory(currentAuction.getAuctionId());
      if (res.isSuccess() && res.getBids() != null) {
        Platform.runLater(() -> {
          bidHistoryList.setAll(res.getBids());
          bidCount = res.getBids().size();
          lblTotalBids.setText(String.valueOf(bidCount));
        });
      }
    }, "load-bid-history").start();
  }

  // ── COUNTDOWN ─────────────────────────────────────────────────────────────

  private void startCountdownTimer() {
    if (countdownTimeline != null) countdownTimeline.stop();
    countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
      if (auctionEndTime == null) return;
      long secondsLeft = LocalDateTime.now().until(auctionEndTime, ChronoUnit.SECONDS);
      if (secondsLeft <= 0) {
        lblCountdown.setText("00:00:00");
        stopCountdown();
        disableBidActions();
        return;
      }
      long hours   = secondsLeft / 3600;
      long minutes = (secondsLeft % 3600) / 60;
      long seconds = secondsLeft % 60;
      lblCountdown.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
      lblCountdown.setStyle(secondsLeft < 60
          ? "-fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;"
          : "-fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
    }));
    countdownTimeline.setCycleCount(Timeline.INDEFINITE);
    countdownTimeline.play();
  }

  private void stopCountdown() {
    if (countdownTimeline != null) countdownTimeline.stop();
  }

  private void disableBidActions() {
    txtBidAmount.setDisable(true);
    chkAutoBid.setDisable(true);
    txtAutoBidMax.setDisable(true);
  }

  // ── QUICK BID ─────────────────────────────────────────────────────────────

  @FXML public void handleQuickBid10k()  { txtBidAmount.setText(currentPrice.add(new BigDecimal("10000")).toPlainString()); }
  @FXML public void handleQuickBid50k()  { txtBidAmount.setText(currentPrice.add(new BigDecimal("50000")).toPlainString()); }
  @FXML public void handleQuickBid100k() { txtBidAmount.setText(currentPrice.add(new BigDecimal("100000")).toPlainString()); }

  // ── PLACE BID ─────────────────────────────────────────────────────────────

  @FXML
  public void handlePlaceBid() {
    lblBidError.setVisible(false);
    String amtStr = txtBidAmount.getText().trim();
    if (amtStr.isEmpty()) { showBidError("Vui lòng nhập giá bid"); return; }

    BigDecimal bidAmount;
    try { bidAmount = new BigDecimal(amtStr); }
    catch (NumberFormatException e) { showBidError("Giá bid không hợp lệ"); return; }

    if (bidAmount.compareTo(currentPrice) <= 0) {
      showBidError("Giá bid phải lớn hơn giá hiện tại (" + formatMoney(currentPrice) + " VNĐ)");
      return;
    }
    if (currentAuction == null) { showBidError("Chưa chọn phiên đấu giá"); return; }
    if (ClientSession.getCurrentUser() == null) { showBidError("Bạn chưa đăng nhập"); return; }

    // FIX 3: BidRequest nhận auctionId là String, truyền đúng kiểu
    BidRequest req = new BidRequest(
        ClientSession.getCurrentUser().getId(),
        String.valueOf(currentAuction.getAuctionId()),
        bidAmount
    );

    new Thread(() -> {
      BidResponse res = SocketClient.getInstance().placeBid(req);
      Platform.runLater(() -> {
        if (res.isSuccess()) {
          txtBidAmount.clear();
        } else {
          showBidError(res.getMessage());
        }
      });
    }, "place-bid-thread").start();
  }

  // ── AUTO BID ──────────────────────────────────────────────────────────────

  @FXML
  public void handleEnableAutoBid() {
    lblBidError.setVisible(false);
    if (!chkAutoBid.isSelected()) { showBidError("Bạn chưa bật Auto-bid"); return; }
    if (txtAutoBidMax.getText().trim().isEmpty()) { showBidError("Nhập giá tối đa auto-bid"); return; }

    BigDecimal max;
    try { max = new BigDecimal(txtAutoBidMax.getText().trim()); }
    catch (NumberFormatException e) { showBidError("Giá tối đa không hợp lệ"); return; }

    if (max.compareTo(currentPrice) <= 0) {
      showBidError("Giá tối đa phải lớn hơn giá hiện tại");
      return;
    }
    if (currentAuction == null || ClientSession.getCurrentUser() == null) {
      showBidError("Lỗi session"); return;
    }

    // FIX 4: AutoBidConfig constructor là (bidderId, bidderUsername, auctionId, maxBid, increment)
    AutoBidConfig config = new AutoBidConfig(
        ClientSession.getCurrentUser().getId(),
        ClientSession.getCurrentUser().getUsername(),
        currentAuction.getAuctionId(),
        max,
        stepPrice   // dùng stepPrice làm increment mặc định
    );

    new Thread(() -> {
      com.auction.common.response.SimpleResponse res =
          SocketClient.getInstance().registerAutoBid(config);
      Platform.runLater(() -> {
        if (!res.isSuccess()) showBidError("Lỗi auto-bid: " + res.getMessage());
      });
    }, "register-autobid-thread").start();
  }

  // ── BID HISTORY REFRESH ───────────────────────────────────────────────────

  @FXML
  public void handleRefreshBidHistory() {
    loadBidHistory();
  }

  // ── SELLER ACTIONS ────────────────────────────────────────────────────────

  @FXML public void handleViewSellerProfile() { /* TODO */ }

  // ── SETUP HELPERS ─────────────────────────────────────────────────────────

  private void setupBidHistoryTable() {
    tblBidHistory.setItems(bidHistoryList);
    colBidTime.setCellValueFactory(data -> {
      LocalDateTime t = data.getValue().getCreatedAt();
      return new SimpleStringProperty(t != null ? t.format(timeFormatter) : "");
    });
    colBidUser.setCellValueFactory(data ->
        new SimpleStringProperty("User#" + data.getValue().getBidderId()));
    colBidAmount.setCellValueFactory(data ->
        new SimpleStringProperty(formatMoney(data.getValue().getAmount()) + " VNĐ"));
    colBidType.setCellValueFactory(data ->
        new SimpleStringProperty(data.getValue().isAutoBid() ? "AUTO" : "MANUAL"));
  }

  private void setupAutoBidToggle() {
    chkAutoBid.selectedProperty().addListener((obs, oldVal, newVal) ->
        txtAutoBidMax.setDisable(!newVal));
  }

  private void setupNumberOnlyFields() {
    addNumberOnlyListener(txtBidAmount);
    addNumberOnlyListener(txtAutoBidMax);
  }

  private void addNumberOnlyListener(TextField field) {
    if (field == null) return;
    field.textProperty().addListener((obs, oldVal, newVal) -> {
      if (newVal != null && !newVal.matches("\\d*"))
        field.setText(newVal.replaceAll("[^\\d]", ""));
    });
  }

  private void updateCurrentPriceUI() {
    String formatted = formatMoney(currentPrice) + " VNĐ";
    if (lblCurrentPrice != null)      lblCurrentPrice.setText(formatted);
    if (lblCurrentPriceRight != null) lblCurrentPriceRight.setText(formatted);
  }

  private void updateYourStatus() {
    if (lblYourStatus == null || currentAuction == null
        || ClientSession.getCurrentUser() == null) return;
    if (currentAuction.getHighestBidderId() == ClientSession.getCurrentUser().getId()) {
      lblYourStatus.setText("✅ Bạn đang dẫn đầu!");
      lblYourStatus.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 12px; -fx-font-weight: bold;");
    } else {
      lblYourStatus.setText("⚠ Bạn đang bị vượt giá!");
      lblYourStatus.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 12px; -fx-font-weight: bold;");
    }
  }

  private void showBidError(String msg) {
    if (lblBidError == null) return;
    lblBidError.setText(msg);
    lblBidError.setVisible(true);
  }

  private String formatMoney(BigDecimal value) {
    if (value == null) return "0";
    return moneyFormat.format(value);
  }
}