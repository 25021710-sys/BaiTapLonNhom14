package com.auction.client.controller;

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
import javafx.scene.layout.VBox;
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

  // ── AUCTION INFO ──────────────────────────────────────────────────────────
  @FXML private Label lblAuctionId;
  @FXML private Label lblStartTime;
  @FXML private Label lblEndTime;
  @FXML private Label lblParticipants;

  // ── BID HISTORY CHART (optional, nếu có fx:id trong FXML) ────────────────
  @FXML private LineChart<Number, Number> bidHistoryChart;
  @FXML private NumberAxis chartXAxis;
  @FXML private NumberAxis chartYAxis;

  // Thêm field
  @FXML private Label lblMyBalance;

  @FXML private TabPane auctionTabs;

  @FXML private VBox countdownSection;
  @FXML private VBox priceSection;
  @FXML private VBox placeBidSection;
  @FXML private VBox autoBidSection;

  @FXML private Label lblPriceTitleRight;

  // ── STATE ─────────────────────────────────────────────────────────────────
  private AuctionDTO currentAuction;
  private BigDecimal currentPrice = BigDecimal.ZERO;
  private BigDecimal stepPrice    = new BigDecimal("50000");
  private LocalDateTime auctionEndTime;
  private Timeline countdownTimeline;
  // Debounce: chỉ load lại lịch sử tối đa 1 lần/500ms dù nhận nhiều push liên tiếp
  private Timeline bidHistoryDebounce;
  private int bidCount = 0;
  private boolean auctionEnded = false;           // FIX: thêm field bị thiếu
  private int currentHighestBidderId = -1;        // FIX: track highest bidder riêng
  private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM");
  private final DecimalFormat moneyFormat = new DecimalFormat("#,###");
  private final ObservableList<BidTransaction> bidHistoryList = FXCollections.observableArrayList();

  // For bid price chart
  private XYChart.Series<Number, Number> priceSeries;
  private long chartStartMs;

  private final java.util.Map<Integer, String> usernameCache = new java.util.HashMap<>();

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
        ? auction.getCurrentPrice()
        : auction.getStartingPrice();

    this.auctionEndTime = auction.getEndTime();
    this.chartStartMs = System.currentTimeMillis();
    this.currentHighestBidderId = auction.getHighestBidderId();

    String status = auction.getStatus() != null ? auction.getStatus().name() : "";

    Platform.runLater(() -> {

      // ── Top info
      lblAuctionTitle.setText("Đấu giá: " + auction.getItemName());
      if ("OPEN".equals(status)) {
        lblAuctionStatus.setText("SẮP DIỄN RA");
        lblAuctionStatus.getStyleClass().setAll("badge-open");
      } else if ("RUNNING".equals(status)) {
        lblAuctionStatus.setText("ĐANG DIỄN RA");
        lblAuctionStatus.getStyleClass().setAll("badge-running");
      } else {
        lblAuctionStatus.setText("ĐÃ KẾT THÚC");
        lblAuctionStatus.getStyleClass().setAll("badge-ended");
      }

      lblCategory.setText(auction.getItemCategory());
      lblStartPrice.setText(formatMoney(auction.getStartingPrice()) + " VNĐ");
      lblStepPrice.setText(formatMoney(stepPrice) + " VNĐ");

      lblTotalBids.setText(String.valueOf(auction.getTotalBids()));
      lblLeadingUser.setText(
          auction.getHighestBidderUsername() != null
              ? auction.getHighestBidderUsername()
              : "---"
      );

      if (auction.getItemDescription() != null)
        txtDescription.setText(auction.getItemDescription());

      lblAuctionId.setText("Auction ID: #" + auction.getAuctionId());
      lblStartTime.setText("Bắt đầu: " + auction.getStartTime().format(timeFormatter));
      lblEndTime.setText("Kết thúc: " + auction.getEndTime().format(timeFormatter));

      lblSellerName.setText("Seller: " + auction.getSellerName());

      // Balance
      if (ClientSession.getCurrentUser() != null && lblMyBalance != null) {
        lblMyBalance.setText(formatMoney(ClientSession.getCurrentUser().getBalance()) + " VNĐ");
      }

      // ── Update status UI (winner/loser)
      updateYourStatus();

      // ── Load image
      loadProductImage(auction.getImageUrl());

      // ───────────────────────────────────────────────
      // Sidebar PRICE TITLE + VALUE theo status
      // ───────────────────────────────────────────────
      if (lblPriceTitleRight != null && lblCurrentPriceRight != null) {

        if ("OPEN".equals(status)) {
          lblPriceTitleRight.setText("💰  Giá khởi điểm");
          lblCurrentPriceRight.setText(formatMoney(auction.getStartingPrice()) + " VNĐ");

        } else if ("RUNNING".equals(status)) {
          lblPriceTitleRight.setText("💰  Giá hiện tại");
          lblCurrentPriceRight.setText(formatMoney(currentPrice) + " VNĐ");

        } else if ("FINISHED".equals(status)) {
          lblPriceTitleRight.setText("💰  Giá chốt phiên");

          BigDecimal finalPrice = auction.getCurrentPrice() != null
              ? auction.getCurrentPrice()
              : auction.getStartingPrice();

          lblCurrentPriceRight.setText(formatMoney(finalPrice) + " VNĐ");
        }
      }

      // ───────────────────────────────────────────────
      // Hide/Show Tabs + Sidebar sections theo status
      // ───────────────────────────────────────────────
      boolean isOpen = "OPEN".equals(status);
      boolean isRunning = "RUNNING".equals(status);
      boolean isFinished = "FINISHED".equals(status);

// Tabs: OPEN thì ẩn, còn RUNNING/FINISHED thì hiện
      if (auctionTabs != null) {
        auctionTabs.setVisible(!isOpen);
        auctionTabs.setManaged(!isOpen);
      }

// Countdown chỉ hiện khi RUNNING
      if (countdownSection != null) {
        countdownSection.setVisible(isRunning);
        countdownSection.setManaged(isRunning);
      }

// Price section: LUÔN HIỆN (OPEN/RUNNING/FINISHED đều thấy)
      if (priceSection != null) {
        priceSection.setVisible(true);
        priceSection.setManaged(true);
      }

// Place bid + Auto bid: chỉ hiện khi RUNNING
      if (placeBidSection != null) {
        placeBidSection.setVisible(isRunning);
        placeBidSection.setManaged(isRunning);
      }

      if (autoBidSection != null) {
        autoBidSection.setVisible(isRunning);
        autoBidSection.setManaged(isRunning);
      }

      if (lblYourStatus != null) {
        boolean showStatus = isRunning;
        lblYourStatus.setVisible(showStatus);
        lblYourStatus.setManaged(showStatus);
      }

      if (lblPriceTitleRight != null && lblCurrentPriceRight != null) {

        if (isOpen) {
          lblPriceTitleRight.setText("💰  Giá khởi điểm");
          lblCurrentPriceRight.setText(formatMoney(auction.getStartingPrice()) + " VNĐ");

        } else if (isRunning) {
          lblPriceTitleRight.setText("💰  Giá hiện tại");
          lblCurrentPriceRight.setText(formatMoney(currentPrice) + " VNĐ");

        } else if (isFinished) {
          lblPriceTitleRight.setText("💰  Giá chốt phiên");

          BigDecimal finalPrice = auction.getCurrentPrice() != null
              ? auction.getCurrentPrice()
              : auction.getStartingPrice();

          lblCurrentPriceRight.setText(formatMoney(finalPrice) + " VNĐ");
        }
      }
    });

    // ── Subscribe realtime
    subscribeRealtime(auction.getAuctionId());

    // ── Load bid history
    loadBidHistory();

    // ── Countdown chỉ chạy khi RUNNING
    if ("RUNNING".equals(status)) {
      startCountdownTimer();
    } else {
      stopCountdown();
    }
  }

  // ── SUBSCRIBE REALTIME ────────────────────────────────────────────────────

  private void subscribeRealtime(int auctionId) {
    // addPushCallback thay vì setPushCallback — không overwrite callback của màn hình khác
    SocketClient.getInstance().addPushCallback(this::handlePushUpdate);

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
   * Dọn dẹp khi màn hình bị đóng: hủy subscribe server + remove push callback.
   * Gọi từ onCloseRequest của Stage (xem DashboardController hoặc nơi mở Stage này).
   *
   * Ví dụ dùng:
   *   stage.setOnCloseRequest(e -> auctionRoomController.cleanup());
   */
  public void cleanup() {
    stopCountdown();
    if (bidHistoryDebounce != null) bidHistoryDebounce.stop();
    SocketClient.getInstance().removePushCallback(this::handlePushUpdate);
    // Báo server bỏ subscribe (giảm participant count)
    if (currentAuction != null) {
      new Thread(() ->
              SocketClient.getInstance().unsubscribeAuction(currentAuction.getAuctionId()),
              "unsubscribe-thread"
      ).start();
    }
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
        // Xóa thông báo lỗi cũ khi có update mới — trạng thái đã được refresh
        if (lblBidError != null) lblBidError.setVisible(false);
        if (update.getType() == AuctionUpdateDTO.UpdateType.AUCTION_EXTENDED) {}
        addChartPoint(currentPrice);
        scheduleBidHistoryReload(); // debounced — tránh flood request khi nhiều bid liên tiếp
        if (lblPriceTitleRight != null)
          lblPriceTitleRight.setText("💰  Giá hiện tại");
      }
      case AUCTION_ENDED -> {
        auctionEnded = true;
        lblAuctionStatus.setText("ĐÃ KẾT THÚC");
        lblAuctionStatus.getStyleClass().setAll("badge-ended");
        stopCountdown();
        disableBidActions();
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

  /**
   * Debounce: nếu nhận nhiều push BID_PLACED liên tiếp trong 500ms,
   * chỉ thực sự gọi loadBidHistory() 1 lần sau khi burst dừng lại.
   * Tránh nhiều background thread đồng thời tranh responseQueue gây lẫn response.
   */
  private void scheduleBidHistoryReload() {
    if (bidHistoryDebounce != null) bidHistoryDebounce.stop();
    bidHistoryDebounce = new Timeline(new KeyFrame(Duration.millis(500), e -> loadBidHistory()));
    bidHistoryDebounce.setCycleCount(1);
    bidHistoryDebounce.play();
  }

  private void loadBidHistory() {
    if (currentAuction == null) return;
    new Thread(() -> {
      BidHistoryResponse res = SocketClient.getInstance().getBidHistory(currentAuction.getAuctionId());
      if (res.isSuccess() && res.getBids() != null) {
        // Collect tất cả bidderId chưa có trong cache
        java.util.Set<Integer> unknownIds = new java.util.HashSet<>();
        for (BidTransaction b : res.getBids()) {
          if (!usernameCache.containsKey(b.getBidderId()))
            unknownIds.add(b.getBidderId());
        }

        // Resolve username hàng loạt từ server (1 request)
        if (!unknownIds.isEmpty()) {
          java.util.Map<Integer, String> resolved =
                  SocketClient.getInstance().resolveUsernames(unknownIds);
          if (resolved != null) usernameCache.putAll(resolved);
        }

        Platform.runLater(() -> {
          bidHistoryList.setAll(res.getBids());
          bidCount = res.getBids().size();
          lblTotalBids.setText(String.valueOf(bidCount));
          tblBidHistory.refresh(); // FIX: force re-render để hiện username mới
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
          if (lblBidError != null) lblBidError.setVisible(false);

          // FIX RACE CONDITION:
          // handlePushUpdate (từ server broadcast) có thể đã chạy trước trên FX thread
          // và đã set currentPrice = giá của người BID SAU (cao hơn giá của mình).
          // Nếu ta ghi đè currentPrice = res.getCurrentHighestBid() (giá cũ hơn) → UI lùi về trước.
          //
          // Quy tắc: chỉ áp dụng BidResponse nếu giá server trả về CAO HƠN giá hiện tại.
          // Nếu push đã cập nhật giá mới hơn rồi → bỏ qua, tin vào push.
          BigDecimal responsePrice = res.getCurrentHighestBid();
          if (responsePrice != null && responsePrice.compareTo(currentPrice) > 0) {
            currentPrice = responsePrice;
            currentHighestBidderId = ClientSession.getCurrentUser().getId();
            updateCurrentPriceUI();
            updateYourStatus();
          }
          // Nếu currentPrice đã bằng hoặc cao hơn responsePrice → push đã xử lý đúng,
          // không cần làm gì thêm (updateYourStatus đã được handlePushUpdate gọi rồi).
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
        if (res.isSuccess()) {} else {
          showBidError("Lỗi auto-bid: " + res.getMessage());
        }
      });
    }, "register-autobid-thread").start();
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
        if (res == null || !res.isSuccess() || res.getUser() == null) {
          showBidError("Không tìm được thông tin người bán.");
          return;
        }
        showSellerProfileDialog(res.getUser(), res.getItemCount());
      });
    }, "load-seller-profile").start();
  }

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
    colBidUser.setCellValueFactory(data -> {
      String name = usernameCache.getOrDefault(
          data.getValue().getBidderId(), "User#" + data.getValue().getBidderId());
      return new SimpleStringProperty(name);
    });
    colBidAmount.setCellValueFactory(data ->
        new SimpleStringProperty(formatMoney(data.getValue().getAmount()) + " VND"));
    colBidType.setCellValueFactory(data ->
        new SimpleStringProperty(data.getValue().isAutoBid() ? "AUTO" : "MANUAL"));

    // Force mau chu trang ro
    javafx.util.Callback<TableColumn<BidTransaction, String>,
        TableCell<BidTransaction, String>> cellFactory = col -> {
      TableCell<BidTransaction, String> cell = new TableCell<>() {
        @Override
        protected void updateItem(String item, boolean empty) {
          super.updateItem(item, empty);
          if (empty || item == null) {
            setText(null);
            setGraphic(null);
          } else {
            setText(item);
          }
        }
      };
      cell.setStyle("-fx-text-fill: #2C3E50; -fx-alignment: CENTER-LEFT;");
      return cell;
    };

    colBidTime.setCellFactory(cellFactory);
    colBidUser.setCellFactory(cellFactory);
    colBidAmount.setCellFactory(cellFactory);
    colBidType.setCellFactory(cellFactory);

    // Bo sung style cho table
    tblBidHistory.setStyle("-fx-background-color: white;");
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

  private void showBidError(String msg) {
    if (lblBidError == null) return;
    lblBidError.setText(msg);
    lblBidError.setVisible(true);
  }

  private String formatMoney(BigDecimal value) {
    if (value == null) return "0";
    return moneyFormat.format(value);
  }

  private void showSellerProfileDialog(com.auction.common.dto.UserDTO u, int itemCount) {
    javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(0);
    root.setPrefWidth(360);
    root.setStyle("-fx-background-color: #1a1f2e; -fx-background-radius: 14;");

    // ── Header ──────────────────────────────────────────
    javafx.scene.layout.VBox header = new javafx.scene.layout.VBox(6);
    header.setAlignment(javafx.geometry.Pos.CENTER);
    header.setStyle(
            "-fx-background-color: #252b3b;" +
                    "-fx-background-radius: 14 14 0 0;" +
                    "-fx-padding: 28 20 20 20;"
    );

    javafx.scene.shape.Circle avatarCircle = new javafx.scene.shape.Circle(38);
    avatarCircle.setFill(javafx.scene.paint.Color.web("#1f93ff"));
    String initial = (u.getUsername() != null && !u.getUsername().isEmpty())
            ? String.valueOf(u.getUsername().charAt(0)).toUpperCase() : "?";
    javafx.scene.control.Label avatarLbl = new javafx.scene.control.Label(initial);
    avatarLbl.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: white;");
    javafx.scene.layout.StackPane avatarPane = new javafx.scene.layout.StackPane(avatarCircle, avatarLbl);

    javafx.scene.control.Label nameLbl = new javafx.scene.control.Label(
            u.getUsername() != null ? u.getUsername() : "—");
    nameLbl.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");

    String joinDate = (u.getCreatedAt() != null)
            ? u.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            : "--";
    javafx.scene.control.Label joinLbl = new javafx.scene.control.Label("Tham gia từ: " + joinDate);
    joinLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #8a9bb0;");

    header.getChildren().addAll(avatarPane, nameLbl, joinLbl);

    // ── Stats bar ────────────────────────────────────────
    javafx.scene.layout.HBox statsBar = new javafx.scene.layout.HBox(0);
    statsBar.setStyle("-fx-background-color: #1f93ff; -fx-padding: 12 0;");

    javafx.scene.layout.VBox statBox = new javafx.scene.layout.VBox(2);
    statBox.setAlignment(javafx.geometry.Pos.CENTER);
    statBox.setMaxWidth(Double.MAX_VALUE);
    javafx.scene.layout.HBox.setHgrow(statBox, javafx.scene.layout.Priority.ALWAYS);

    javafx.scene.control.Label statNum = new javafx.scene.control.Label(String.valueOf(itemCount));
    statNum.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: white;");
    javafx.scene.control.Label statTxt = new javafx.scene.control.Label("Sản phẩm đã đăng");
    statTxt.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(255,255,255,0.85);");
    statBox.getChildren().addAll(statNum, statTxt);
    statsBar.getChildren().add(statBox);

    // ── Info rows ────────────────────────────────────────
    javafx.scene.layout.VBox infoSection = new javafx.scene.layout.VBox(0);
    infoSection.setStyle("-fx-padding: 4 20 12 20;");

    String location = (u.getLocation() != null && !u.getLocation().isBlank())
            ? u.getLocation() : "Chưa cập nhật";
    String desc = (u.getDescription() != null && !u.getDescription().isBlank())
            ? u.getDescription() : "Chưa có giới thiệu";

    infoSection.getChildren().addAll(
            makeInfoRow("📍  Vị trí",      location),
            makeSeparator(),
            makeInfoRow("📝  Giới thiệu",  desc)
    );

    // ── Close button ─────────────────────────────────────
    javafx.scene.control.Button closeBtn = new javafx.scene.control.Button("Đóng");
    closeBtn.setMaxWidth(Double.MAX_VALUE);
    closeBtn.setStyle(
            "-fx-background-color: #1f93ff; -fx-text-fill: white;" +
                    "-fx-font-weight: bold; -fx-font-size: 13px;" +
                    "-fx-background-radius: 0 0 14 14; -fx-padding: 12; -fx-cursor: hand;"
    );
    closeBtn.setOnMouseEntered(e ->
            closeBtn.setStyle(closeBtn.getStyle().replace("#1f93ff", "#1a7fd4")));
    closeBtn.setOnMouseExited(e ->
            closeBtn.setStyle(closeBtn.getStyle().replace("#1a7fd4", "#1f93ff")));

    root.getChildren().addAll(header, statsBar, infoSection, closeBtn);

    javafx.stage.Stage stage = new javafx.stage.Stage();
    stage.setTitle("Hồ sơ người bán");
    stage.setScene(new javafx.scene.Scene(root));
    stage.setResizable(false);
    stage.initStyle(javafx.stage.StageStyle.DECORATED);
    closeBtn.setOnAction(e -> stage.close());
    stage.show();
  }

  private javafx.scene.layout.HBox makeInfoRow(String key, String value) {
    javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(12);
    row.setStyle("-fx-padding: 10 0;");
    row.setAlignment(javafx.geometry.Pos.TOP_LEFT);

    javafx.scene.control.Label keyLbl = new javafx.scene.control.Label(key);
    keyLbl.setMinWidth(110);
    keyLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #8a9bb0;");

    javafx.scene.control.Label valLbl = new javafx.scene.control.Label(value);
    valLbl.setWrapText(true);
    valLbl.setMaxWidth(210);
    valLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: white; -fx-font-weight: bold;");
    javafx.scene.layout.HBox.setHgrow(valLbl, javafx.scene.layout.Priority.ALWAYS);

    row.getChildren().addAll(keyLbl, valLbl);
    return row;
  }

  private javafx.scene.control.Separator makeSeparator() {
    javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
    sep.setStyle("-fx-background-color: #2d3448; -fx-opacity: 0.5;");
    return sep;
  }

  private String formatMoney(double value) {
    return moneyFormat.format(value);
  }
}