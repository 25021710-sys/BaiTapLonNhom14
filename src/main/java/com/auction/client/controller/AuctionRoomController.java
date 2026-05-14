package com.auction.client.controller;

import com.auction.common.dto.UserDTO;
import com.auction.common.response.GetUserProfileResponse;
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
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * AuctionRoomController – màn hình đấu giá trực tiếp (realtime bidding).
 *
 * Kết nối thực tế với server qua SocketClient.
 * Subscribe nhận push update (Observer pattern) từ AuctionManager.
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

  // ── CHAT ──────────────────────────────────────────────────────────────────
  @FXML private ListView<String> lvChatMessages;
  @FXML private TextField txtChatInput;

  // ── RIGHT PANEL ───────────────────────────────────────────────────────────
  @FXML private Label lblCountdown;
  @FXML private Label lblCurrentPriceRight;
  @FXML private Label lblYourStatus;
  @FXML private TextField txtBidAmount;
  @FXML private Label lblBidError;

  // ── AUTO BID ──────────────────────────────────────────────────────────────
  @FXML private CheckBox chkAutoBid;
  @FXML private TextField txtAutoBidMax;
  @FXML private TextField txtAutoBidIncrement;

  // ── SELLER INFO ───────────────────────────────────────────────────────────
  @FXML private Label lblSellerName;
  @FXML private Label lblSellerRating;
  @FXML private Label lblSellerProducts;

  // ── AUCTION INFO ──────────────────────────────────────────────────────────
  @FXML private Label lblAuctionId;
  @FXML private Label lblStartTime;
  @FXML private Label lblEndTime;
  @FXML private Label lblParticipants;

  // ── BID HISTORY CHART (optional, nếu có fx:id trong FXML) ────────────────
  @FXML private LineChart<Number, Number> bidHistoryChart;
  @FXML private NumberAxis chartXAxis;
  @FXML private NumberAxis chartYAxis;

  // ── STATE ─────────────────────────────────────────────────────────────────
  private AuctionDTO currentAuction;
  private BigDecimal currentPrice = BigDecimal.ZERO;
  private BigDecimal stepPrice    = new BigDecimal("50000");
  private LocalDateTime auctionEndTime;
  private Timeline countdownTimeline;
  private int bidCount = 0;
  private boolean auctionEnded = false;           // FIX: thêm field bị thiếu
  private int currentHighestBidderId = -1;        // FIX: track highest bidder riêng
  private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM");
  private final DecimalFormat moneyFormat = new DecimalFormat("#,###");
  private final ObservableList<BidTransaction> bidHistoryList = FXCollections.observableArrayList();

  // For bid price chart
  private XYChart.Series<Number, Number> priceSeries;
  private long chartStartMs;

  // ── INIT ──────────────────────────────────────────────────────────────────

  @FXML
  public void initialize() {
    setupBidHistoryTable();
    setupAutoBidToggle();
    setupNumberOnlyFields();
    cbBidFilter.setItems(FXCollections.observableArrayList("Tất cả", "Chỉ của tôi", "Auto-bid"));
    cbBidFilter.setValue("Tất cả");
    setupChart();
    lblBidError.setVisible(false);
  }

  /**
   * Được gọi từ DashboardController khi user click vào 1 phiên đấu giá.
   * Load dữ liệu thực từ server và bắt đầu subscribe realtime update.
   */
  public void loadAuction(AuctionDTO auction) {
    this.currentAuction = auction;
    this.currentPrice = auction.getCurrentPrice() != null
            ? auction.getCurrentPrice() : auction.getStartingPrice();
    this.auctionEndTime = auction.getEndTime();
    this.chartStartMs = System.currentTimeMillis();
    this.currentHighestBidderId = auction.getHighestBidderId(); // FIX: init từ DTO

    // Hiển thị thông tin
    Platform.runLater(() -> {
      lblAuctionTitle.setText("Đấu giá: " + auction.getItemName());
      lblAuctionStatus.setText(String.valueOf(auction.getStatus()));
      lblCategory.setText(auction.getItemCategory());
      lblStartPrice.setText(formatMoney(auction.getStartingPrice()) + " VNĐ");
      lblStepPrice.setText(formatMoney(stepPrice) + " VNĐ");
      lblTotalBids.setText(String.valueOf(auction.getTotalBids()));
      lblLeadingUser.setText(auction.getHighestBidderUsername() != null
              ? auction.getHighestBidderUsername() : "---");
      if (auction.getItemDescription() != null)
        txtDescription.setText(auction.getItemDescription());
      lblAuctionId.setText("Auction ID: #" + auction.getAuctionId());
      lblStartTime.setText("Bắt đầu: " + auction.getStartTime().format(timeFormatter));
      lblEndTime.setText("Kết thúc: " + auction.getEndTime().format(timeFormatter));
      lblSellerName.setText("Seller: " + auction.getSellerName());
      updateCurrentPriceUI();
      updateYourStatus();
      loadProductImage(auction.getImageUrl());
    });

    // Subscribe realtime
    subscribeRealtime(auction.getAuctionId());

    // Load bid history
    loadBidHistory();

    // Start countdown
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
      // Server sẽ push PARTICIPANT_CHANGED ngay sau subscribe.
      // lblParticipants sẽ được cập nhật trong handlePushUpdate.
    }, "subscribe-thread").start();
  }

  /**
   * Nhận push update từ server (Observer callback).
   * Được gọi trên FX thread nhờ Platform.runLater() trong SocketClient.
   */
  private void handlePushUpdate(AuctionUpdateDTO update) {
    if (currentAuction == null || update.getAuctionId() != currentAuction.getAuctionId()) return;

    switch (update.getType()) {
      case BID_PLACED, AUCTION_EXTENDED -> {
        currentPrice = update.getNewPrice();
        currentHighestBidderId = update.getHighestBidderId(); // FIX: update live
        auctionEndTime = update.getNewEndTime() != null ? update.getNewEndTime() : auctionEndTime;
        bidCount++;
        updateCurrentPriceUI();
        lblTotalBids.setText(String.valueOf(bidCount));
        lblLeadingUser.setText(update.getHighestBidderUsername() != null
                ? update.getHighestBidderUsername() : "---");
        updateYourStatus();
        if (update.getType() == AuctionUpdateDTO.UpdateType.AUCTION_EXTENDED) {
          lvChatMessages.getItems().add("[SYSTEM] ⏱ Phiên được gia hạn thêm 60 giây!");
        }
        addChartPoint(currentPrice);
        loadBidHistory(); // refresh table
      }
      case AUCTION_ENDED -> {
        auctionEnded = true;
        lblAuctionStatus.setText("ĐÃ KẾT THÚC");
        lblAuctionStatus.getStyleClass().setAll("badge-ended");
        stopCountdown();
        disableBidActions();
        lvChatMessages.getItems().add("[SYSTEM] 🏆 Phiên đấu giá đã kết thúc! Người thắng: "
                + update.getHighestBidderUsername());
      }
      case PARTICIPANT_CHANGED -> {
        if (lblParticipants != null) {
          lblParticipants.setText(String.valueOf(update.getParticipantCount()));
        }
      }
      default -> {}
    }
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
          // Update chart
          if (priceSeries != null) {
            priceSeries.getData().clear();
            for (BidTransaction b : res.getBids()) {
              long ms = b.getCreatedAt() != null
                      ? java.time.Duration.between(
                      currentAuction.getStartTime(), b.getCreatedAt()).getSeconds()
                      : 0;
              priceSeries.getData().add(
                      new XYChart.Data<>(ms, b.getAmount().doubleValue()));
            }
          }
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
      long days    = secondsLeft / 86400;
      long hours   = (secondsLeft % 86400) / 3600;
      long minutes = (secondsLeft % 3600) / 60;
      long seconds = secondsLeft % 60;
      if (days > 0)
        lblCountdown.setText(String.format("%dd %02d:%02d:%02d", days, hours, minutes, seconds));
      else
        lblCountdown.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
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
    if (txtAutoBidIncrement != null) txtAutoBidIncrement.setDisable(true);
  }

  // ── QUICK BID ─────────────────────────────────────────────────────────────

  // FIX: luôn cộng vào currentPrice (giá server mới nhất), không đọc từ textfield
  @FXML public void handleQuickBid10k()  { setBidAmount(currentPrice.add(new BigDecimal("10000"))); }
  @FXML public void handleQuickBid50k()  { setBidAmount(currentPrice.add(new BigDecimal("50000"))); }
  @FXML public void handleQuickBid100k() { setBidAmount(currentPrice.add(new BigDecimal("100000"))); }

  private void setBidAmount(BigDecimal value) {
    if (txtBidAmount != null)
      // setScale(0, DOWN) loại bỏ phần thập phân trước khi setText,
      // tránh việc toPlainString() trả về "1501100000.00" rồi listener
      // xóa dấu "." nhưng giữ lại "00" → nhân nhầm 100 lần.
      txtBidAmount.setText(value.setScale(0, java.math.RoundingMode.DOWN).toPlainString());
  }

  // ── PLACE BID ─────────────────────────────────────────────────────────────

  @FXML
  public void handlePlaceBid() {
    if (lblBidError != null) lblBidError.setVisible(false);
    if (auctionEnded) { showBidError("Phiên đấu giá đã kết thúc"); return; }

    String amtStr = txtBidAmount != null ? txtBidAmount.getText().trim() : "";
    if (amtStr.isEmpty()) { showBidError("Vui lòng nhập giá bid"); return; }

    BigDecimal bidAmount;
    try { bidAmount = new BigDecimal(amtStr); }
    catch (NumberFormatException e) { showBidError("Giá bid không hợp lệ"); return; }

    // FIX: kiểm tra đúng bước giá (currentPrice + stepPrice), không chỉ +1
    BigDecimal minValid = currentPrice.add(stepPrice);
    if (bidAmount.compareTo(minValid) < 0) {
      showBidError("Giá tối thiểu: " + formatMoney(minValid) + " VNĐ"
              + " (giá hiện tại + bước " + formatMoney(stepPrice) + " VNĐ)");
      return;
    }

    if (currentAuction == null) { showBidError("Chưa chọn phiên đấu giá"); return; }
    if (ClientSession.getCurrentUser() == null) { showBidError("Bạn chưa đăng nhập"); return; }

    int userId = ClientSession.getCurrentUser().getId();
    BidRequest req = new BidRequest(userId, String.valueOf(currentAuction.getAuctionId()), bidAmount);

    // Gửi bid trong thread riêng để không block UI
    new Thread(() -> {
      BidResponse res = SocketClient.getInstance().placeBid(req);
      Platform.runLater(() -> {
        if (res != null && res.isSuccess()) {
          if (txtBidAmount != null) txtBidAmount.clear();
          addSystemMessage("[BẠN] Đặt giá thành công: " + formatMoney(res.getCurrentHighestBid()) + " VNĐ ✓");
        } else {
          showBidError(res != null ? res.getMessage() : "Lỗi kết nối server");
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

    BigDecimal increment = stepPrice;
    if (txtAutoBidIncrement != null && !txtAutoBidIncrement.getText().trim().isEmpty()) {
      try { increment = new BigDecimal(txtAutoBidIncrement.getText().trim()); }
      catch (NumberFormatException ignored) {}
    }

    if (currentAuction == null || ClientSession.getCurrentUser() == null) {
      showBidError("Lỗi session"); return;
    }

    AutoBidConfig config = new AutoBidConfig(
            ClientSession.getCurrentUser().getId(),
            ClientSession.getCurrentUser().getUsername(),
            currentAuction.getAuctionId(),
            max, increment
    );

    final BigDecimal maxFinal = max;
    new Thread(() -> {
      com.auction.common.response.SimpleResponse res =
              SocketClient.getInstance().registerAutoBid(config);
      Platform.runLater(() -> {
        if (res.isSuccess()) {
          lvChatMessages.getItems().add("[SYSTEM] Auto-bid đã bật. Tối đa: "
                  + formatMoney(maxFinal) + " VNĐ");
        } else {
          showBidError("Lỗi auto-bid: " + res.getMessage());
        }
      });
    }, "register-autobid-thread").start();
  }

  @FXML
  public void handleDisableAutoBid() {
    if (currentAuction == null || ClientSession.getCurrentUser() == null) return;
    chkAutoBid.setSelected(false);
    new Thread(() -> {
      SocketClient.getInstance().cancelAutoBid(
              ClientSession.getCurrentUser().getId(), currentAuction.getAuctionId());
      Platform.runLater(() ->
              lvChatMessages.getItems().add("[SYSTEM] Auto-bid đã tắt."));
    }, "cancel-autobid-thread").start();
  }

  // ── CHAT ──────────────────────────────────────────────────────────────────

  @FXML
  public void handleSendChat() {
    String msg = txtChatInput.getText().trim();
    if (msg.isEmpty()) return;
    lvChatMessages.getItems().add("[" + (ClientSession.getCurrentUser() != null
            ? ClientSession.getCurrentUser().getUsername() : "Bạn") + "] " + msg);
    txtChatInput.clear();
  }

  // ── BID HISTORY REFRESH ───────────────────────────────────────────────────

  @FXML
  public void handleRefreshBidHistory() {
    loadBidHistory();
  }

  // ── SELLER ACTIONS ────────────────────────────────────────────────────────

  @FXML
  public void handleViewSellerProfile() {
    if (currentAuction == null) return;
    String sellerName = currentAuction.getSellerName();
    if (sellerName == null || sellerName.isBlank()) return;

    new Thread(() -> {
      GetUserProfileResponse res = SocketClient.getInstance().getSellerProfile(sellerName);
      Platform.runLater(() -> {
        if (res == null || !res.isSuccess() || res.getUser() == null) return;
        UserDTO u = res.getUser();

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Ho so nguoi ban");
        alert.setHeaderText(u.getUsername());
        alert.setContentText(
                "San pham da dang: " + res.getItemCount() + "\n" +
                        "Vai tro: "          + (u.getRole()        != null ? u.getRole()        : "--") + "\n" +
                        "Vi tri: "           + (u.getLocation()    != null ? u.getLocation()    : "--") + "\n" +
                        "Gioi thieu: "       + (u.getDescription() != null ? u.getDescription() : "--") + "\n" +
                        "Tham gia tu: "      + (u.getCreatedAt()   != null
                        ? u.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        : "--")
        );
        alert.showAndWait();
      });
    }, "load-seller-profile").start();
  }

  @FXML public void handleMessageSeller() {}

  // ── SETUP HELPERS ─────────────────────────────────────────────────────────

  private void loadProductImage(String imageUrl) {
    if (imageUrl == null || imageUrl.isBlank()) return;
    new Thread(() -> {
      try {
        Image img = new Image(imageUrl, true); // background loading
        Platform.runLater(() -> {
          if (imgMainProduct != null) {
            imgMainProduct.setImage(img);
            imgMainProduct.setPreserveRatio(true);
            imgMainProduct.setSmooth(true);
          }
          // Hiển thị cùng ảnh cho các thumbnail (hiện chỉ có 1 ảnh)
          Image thumb = new Image(imageUrl, 80, 80, true, true, true);
          if (imgThumb1 != null) imgThumb1.setImage(thumb);
          if (imgThumb2 != null) imgThumb2.setImage(thumb);
          if (imgThumb3 != null) imgThumb3.setImage(thumb);
          if (imgThumb4 != null) imgThumb4.setImage(thumb);
        });
      } catch (Exception e) {
        // Ảnh lỗi → giữ placeholder, không crash app
        System.err.println("[AuctionRoom] Không tải được ảnh: " + imageUrl);
      }
    }, "load-product-image").start();
  }

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

  private void setupChart() {
    if (bidHistoryChart == null) return;
    priceSeries = new XYChart.Series<>();
    priceSeries.setName("Giá đấu");
    bidHistoryChart.getData().add(priceSeries);
    if (chartXAxis != null) chartXAxis.setLabel("Giây");
    if (chartYAxis != null) chartYAxis.setLabel("Giá (VNĐ)");
  }

  private void addChartPoint(BigDecimal price) {
    if (priceSeries == null) return;
    long seconds = (System.currentTimeMillis() - chartStartMs) / 1000;
    priceSeries.getData().add(new XYChart.Data<>(seconds, price.doubleValue()));
  }

  private void setupAutoBidToggle() {
    chkAutoBid.selectedProperty().addListener((obs, oldVal, newVal) -> {
      txtAutoBidMax.setDisable(!newVal);
      if (txtAutoBidIncrement != null) txtAutoBidIncrement.setDisable(!newVal);
    });
  }

  private void setupNumberOnlyFields() {
    addNumberOnlyListener(txtBidAmount);
    addNumberOnlyListener(txtAutoBidMax);
    if (txtAutoBidIncrement != null) addNumberOnlyListener(txtAutoBidIncrement);
  }

  private void addNumberOnlyListener(TextField field) {
    if (field == null) return;
    field.textProperty().addListener((obs, oldVal, newVal) -> {
      if (newVal != null && !newVal.matches("\\d*")) {
        field.setText(newVal.replaceAll("[^\\d]", ""));
      }
    });
  }

  private void updateCurrentPriceUI() {
    String formatted = formatMoney(currentPrice) + " VNĐ";
    if (lblCurrentPrice != null)      lblCurrentPrice.setText(formatted);
    if (lblCurrentPriceRight != null) lblCurrentPriceRight.setText(formatted);
  }

  private void updateYourStatus() {
    if (lblYourStatus == null || ClientSession.getCurrentUser() == null) return;
    if (currentHighestBidderId <= 0) {
      lblYourStatus.setText("Chưa có người đặt giá");
      lblYourStatus.getStyleClass().setAll("status-neutral");
    } else if (currentHighestBidderId == ClientSession.getCurrentUser().getId()) {
      lblYourStatus.setText("✅ Bạn đang dẫn đầu!");
      lblYourStatus.getStyleClass().setAll("status-winning");
    } else {
      lblYourStatus.setText("❌ Bạn đang bị vượt giá!");
      lblYourStatus.getStyleClass().setAll("status-losing");
    }
  }

  private void addSystemMessage(String msg) {
    if (lvChatMessages == null) return;
    Platform.runLater(() -> {
      lvChatMessages.getItems().add(msg);
      lvChatMessages.scrollTo(lvChatMessages.getItems().size() - 1);
    });
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

  private String formatMoney(double value) {
    return moneyFormat.format(value);
  }
}