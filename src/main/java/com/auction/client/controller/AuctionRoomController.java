package com.auction.client.controller;

import com.auction.client.util.ImageUtil;
import com.auction.common.response.GetUserProfileResponse;
import com.auction.client.network.SocketClient;
import com.auction.common.dto.AuctionDTO;
import com.auction.common.dto.AuctionUpdateDTO;
import com.auction.common.request.BidRequest;
import com.auction.common.response.BidHistoryResponse;
import com.auction.common.response.BidResponse;
import com.auction.common.response.CreateAuctionResponse;
import com.auction.server.model.BidTransaction;
import com.auction.client.session.ClientSession;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.CategoryAxis;
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
import java.text.NumberFormat;
import java.util.Locale;

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

  // ── SELLER INFO ───────────────────────────────────────────────────────────
  @FXML private Label lblSellerName;

  // ── AUCTION INFO ──────────────────────────────────────────────────────────
  @FXML private Label lblAuctionId;
  @FXML private Label lblStartTime;
  @FXML private Label lblEndTime;
  @FXML private Label lblParticipants;

  // ── BID HISTORY CHART (optional, nếu có fx:id trong FXML) ────────────────
  @FXML private LineChart<String, Number> bidHistoryChart;
  @FXML private CategoryAxis chartXAxis;
  @FXML private NumberAxis chartYAxis;

  // Thêm field
  @FXML private Label lblMyBalance;

  @FXML private TabPane auctionTabs;

  @FXML private VBox countdownSection;
  @FXML private VBox priceSection;
  @FXML private VBox placeBidSection;

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
  private java.util.List<String> currentImageUrls = new java.util.ArrayList<>();
  private java.util.List<String> currentFullUrls    = new java.util.ArrayList<>();

  // For bid price chart
  private XYChart.Series<String, Number> priceSeries;
  private long chartStartMs;

  private final java.util.Map<Integer, String> usernameCache = new java.util.HashMap<>();

  // ── INIT ──────────────────────────────────────────────────────────────────

  @FXML
  public void initialize() {
    setupBidHistoryTable();
    setupNumberOnlyFields();
    cbBidFilter.setItems(FXCollections.observableArrayList("Tất cả", "Chỉ của tôi"));
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
    this.stepPrice = (auction.getStepPrice() != null
            && auction.getStepPrice().compareTo(BigDecimal.ZERO) > 0)
            ? auction.getStepPrice()
            : new BigDecimal("50000");

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
      loadProductImages(auction.getThumbnailUrls(), auction.getFullImageUrls());      // ───────────────────────────────────────────────
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

// Place bid : chỉ hiện khi RUNNING
      if (placeBidSection != null) {
        placeBidSection.setVisible(isRunning);
        placeBidSection.setManaged(isRunning);
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
    ClientSession.addBalanceListener(this::refreshBalanceLabel);
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
    ClientSession.removeBalanceListener(this::refreshBalanceLabel);
    if (currentAuction != null) {
      // Dùng method — không chờ response
      SocketClient.getInstance()
              .unsubscribeAuctionNoWait(currentAuction.getAuctionId());
    }
  }

  /**
   * Nhận push update từ server (Observer callback).
   * Được gọi trên FX thread nhờ Platform.runLater() trong SocketClient.
   */
  private void handlePushUpdate(AuctionUpdateDTO update) {
    System.out.println("[DEBUG] push: type=" + update.getType()
            + " auctionId=" + update.getAuctionId());
    if (currentAuction == null || update.getAuctionId() != currentAuction.getAuctionId()) return;

    switch (update.getType()) {
      case BID_PLACED, AUCTION_EXTENDED -> {
        int myId = ClientSession.getCurrentUser() != null
                ? ClientSession.getCurrentUser().getId() : -1;

        // Lưu giá CŨ trước khi bị ghi đè — dùng để hoàn tiền đúng số
        BigDecimal previousPrice = currentPrice;
        boolean iWasLeading = (myId != -1 && currentHighestBidderId == myId);
        boolean iJustBid    = (myId != -1 && update.getHighestBidderId() == myId);

        // Cập nhật state
        currentPrice = update.getNewPrice();
        currentHighestBidderId = update.getHighestBidderId();
        auctionEndTime = update.getNewEndTime() != null ? update.getNewEndTime() : auctionEndTime;
        bidCount++;

        // ── Xử lý balance client ──────────────────────────────────────────
        if (!iJustBid && iWasLeading) {
          BigDecimal newBalance = ClientSession.getCurrentUser().getBalance().add(previousPrice);
          ClientSession.updateBalance(newBalance);
        }
        // Nếu mình chưa từng dẫn đầu → không liên quan đến tiền, bỏ qua.

        updateCurrentPriceUI();
        lblTotalBids.setText(String.valueOf(bidCount));
        lblLeadingUser.setText(update.getHighestBidderUsername() != null
                ? update.getHighestBidderUsername() : "---");
        updateYourStatus();
        if (lblBidError != null) lblBidError.setVisible(false);
        // FIX: hiện thông báo gia hạn khi phiên được anti-snipe extend
        if (update.getType() == AuctionUpdateDTO.UpdateType.AUCTION_EXTENDED) {
          if (lblBidError != null) {
            lblBidError.setStyle("-fx-text-fill: orange;");
            lblBidError.setText("⏱ Phiên được gia hạn thêm 60 giây!");
            lblBidError.setVisible(true);
          }
        }
        addChartPoint(currentPrice);
        scheduleBidHistoryReload();
        if (lblPriceTitleRight != null)
          lblPriceTitleRight.setText("💰  Giá hiện tại");
      }
      case AUCTION_ENDED -> {
        auctionEnded = true;
        lblAuctionStatus.setText("ĐÃ KẾT THÚC");
        lblAuctionStatus.getStyleClass().setAll("badge-ended");
        stopCountdown();
        disableBidActions();

        // ── Cập nhật giá chốt cuối cùng lên sidebar
        if (update.getNewPrice() != null) {
          currentPrice = update.getNewPrice();
          if (lblPriceTitleRight != null)
            lblPriceTitleRight.setText("💰  Giá chốt phiên");
          if (lblCurrentPriceRight != null)
            lblCurrentPriceRight.setText(formatMoney(currentPrice) + " VNĐ");
        }

        // ── Ẩn khu vực đặt giá
        if (placeBidSection != null) {
          placeBidSection.setVisible(false);
          placeBidSection.setManaged(false);
        }
        if (countdownSection != null) {
          countdownSection.setVisible(false);
          countdownSection.setManaged(false);
        }

        // ── Hiện popup thông báo kết thúc
        showAuctionEndedDialog(update);

        // ── FIX: Fetch balance + lịch sử mới từ server
        // Server đã cộng tiền cho seller / hoàn tiền bidder thua trong DB,
        // nhưng ClientSession chưa biết → phải chủ động lấy về.
        // Dùng getDepositHistory vì nó trả về cả UserDTO (balance mới) lẫn history.
        int myId = ClientSession.getCurrentUser() != null
                ? ClientSession.getCurrentUser().getId() : -1;
        if (myId != -1) {
          new Thread(() -> {
            com.auction.common.response.BalanceResponse res =
                    SocketClient.getInstance().getDepositHistory(myId);
            if (res != null && res.isSuccess() && res.getData() != null) {
              // updateBalance() sẽ notify tất cả balanceListener đã đăng ký,
              // bao gồm BalanceController (nếu tab đang mở) → tự reload lịch sử
              ClientSession.updateBalance(res.getData().getBalance());
            }
          }, "refresh-balance-after-end").start();
        }
      }
      case PARTICIPANT_CHANGED -> {
        if (lblParticipants != null) {
          lblParticipants.setText(String.valueOf(update.getParticipantCount()));
        }
      }
      case AUCTION_STARTED -> {
        // Phiên vừa chuyển OPEN → RUNNING: cập nhật UI badge + bắt đầu countdown
        lblAuctionStatus.setText("ĐANG DIỄN RA");
        lblAuctionStatus.getStyleClass().setAll("badge-running");
        if (lblPriceTitleRight != null) lblPriceTitleRight.setText("💰  Giá hiện tại");
        if (lblCurrentPriceRight != null && update.getNewPrice() != null) {
          currentPrice = update.getNewPrice();
          lblCurrentPriceRight.setText(formatMoney(currentPrice) + " VNĐ");
        }
        if (update.getNewEndTime() != null) auctionEndTime = update.getNewEndTime();
        startCountdownTimer();
        // Bật lại khu vực đặt giá (nếu bị disable do chưa mở)
        updateYourStatus();
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
    loadBidHistory();
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
          if (resolved != null && !resolved.isEmpty()) usernameCache.putAll(resolved);
          // Fallback: id nào không resolve được thì hiện "User#id" thay vì trống
          for (int id : unknownIds) usernameCache.putIfAbsent(id, "User#" + id);
        }

        Platform.runLater(() -> {
          bidHistoryList.setAll(res.getBids());
          bidCount = res.getBids().size();
          lblTotalBids.setText(String.valueOf(bidCount));
          tblBidHistory.refresh(); // FIX: force re-render để hiện username mới
          // FIX: populate biểu đồ từ lịch sử thực (có timestamp)
          populateChartFromHistory(res.getBids());
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
        // FIX: cập nhật đầy đủ UI khi countdown về 0 (thay vì chờ push AUCTION_ENDED có thể trễ)
        // Chỉ cập nhật badge + ẩn section nếu chưa nhận được push AUCTION_ENDED
        if (!auctionEnded) {
          auctionEnded = true;
          if (lblAuctionStatus != null) {
            lblAuctionStatus.setText("ĐÃ KẾT THÚC");
            lblAuctionStatus.getStyleClass().setAll("badge-ended");
          }
          if (placeBidSection != null) {
            placeBidSection.setVisible(false);
            placeBidSection.setManaged(false);
          }
          if (countdownSection != null) {
            countdownSection.setVisible(false);
            countdownSection.setManaged(false);
          }
          if (lblPriceTitleRight != null) lblPriceTitleRight.setText("💰  Giá chốt phiên");
        }
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
  }

  // ── QUICK BID ─────────────────────────────────────────────────────────────

  // FIX: luôn cộng vào currentPrice (giá server mới nhất), không đọc từ textfield
  @FXML public void handleQuickBid1x() { setBidAmount(currentPrice.add(stepPrice)); }
  @FXML public void handleQuickBid2x() { setBidAmount(currentPrice.add(stepPrice.multiply(new BigDecimal("2")))); }
  @FXML public void handleQuickBid5x() { setBidAmount(currentPrice.add(stepPrice.multiply(new BigDecimal("5")))); }

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
          // Trừ tiền optimistic ngay trên UI để phản hồi nhanh.
          // Push BID_PLACED sẽ nhận iJustBid=true → bỏ qua, không trừ lại → không double-subtract.
          if (ClientSession.getCurrentUser() != null) {
            BigDecimal newBalance = ClientSession.getCurrentUser().getBalance().subtract(bidAmount);
            if (newBalance.compareTo(BigDecimal.ZERO) < 0) newBalance = BigDecimal.ZERO;
            ClientSession.updateBalance(newBalance);
          }
        } else {
          String errMsg = res != null ? res.getMessage() : "Lỗi kết nối server";
          showBidError(errMsg);

          // FIX: Server báo phiên đã kết thúc / bị hủy nhưng client chưa nhận push AUCTION_ENDED
          // (push bị trễ do queue bận hoặc mất gói). Cập nhật UI ngay theo thông báo server.
          if (errMsg != null && (errMsg.contains("đã kết thúc") || errMsg.contains("kết thúc")
                  || errMsg.contains("FINISHED") || errMsg.contains("already ended")
                  || errMsg.contains("Đã hủy") || errMsg.contains("CANCELED"))) {
            boolean canceled = errMsg.contains("Đã hủy") || errMsg.contains("CANCELED");
            handleServerReportedEnd(res.getCurrentHighestBid(), canceled);
          }
        }
      });
    }, "place-bid-thread").start();
  }

  /**
   * Được gọi khi server trả về lỗi "đã kết thúc" nhưng push AUCTION_ENDED chưa tới.
   * Cập nhật UI giống như khi nhận được push AUCTION_ENDED.
   */
  private void handleServerReportedEnd(BigDecimal finalPrice) {
    handleServerReportedEnd(finalPrice, false);
  }

  /** @param isCanceled true → badge "ĐÃ HỦY", false → badge "ĐÃ KẾT THÚC" */
  private void handleServerReportedEnd(BigDecimal finalPrice, boolean isCanceled) {
    if (auctionEnded) return; // đã xử lý rồi, bỏ qua
    auctionEnded = true;
    stopCountdown();
    disableBidActions();

    if (lblAuctionStatus != null) {
      lblAuctionStatus.setText(isCanceled ? "ĐÃ HỦY" : "ĐÃ KẾT THÚC");
      lblAuctionStatus.getStyleClass().setAll("badge-ended");
    }
    if (lblCountdown != null) lblCountdown.setText("00:00:00");

    if (placeBidSection != null) {
      placeBidSection.setVisible(false);
      placeBidSection.setManaged(false);
    }
    if (countdownSection != null) {
      countdownSection.setVisible(false);
      countdownSection.setManaged(false);
    }
    if (lblPriceTitleRight != null) lblPriceTitleRight.setText("💰  Giá chốt phiên");
    if (finalPrice != null && lblCurrentPriceRight != null) {
      lblCurrentPriceRight.setText(formatMoney(finalPrice) + " VNĐ");
    }
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

  private void loadProductImages(java.util.List<String> thumbUrls,
                                 java.util.List<String> fullUrls) {
    if (thumbUrls == null || thumbUrls.isEmpty()) return;

    this.currentImageUrls = thumbUrls;
    this.currentFullUrls  = (fullUrls != null && !fullUrls.isEmpty())
            ? fullUrls : thumbUrls; // fallback nếu không có full

    ImageView[] thumbViews = { imgThumb1, imgThumb2, imgThumb3, imgThumb4 };

    // Ẩn tất cả thumbnail trước
    Platform.runLater(() -> {
      for (ImageView tv : thumbViews) {
        if (tv != null) { tv.setImage(null); tv.setVisible(false); tv.setManaged(false); }
      }
    });

    new Thread(() -> {
      for (int i = 0; i < thumbUrls.size() && i < thumbViews.length; i++) {
        final int idx = i;
        final String thumbUrl = thumbUrls.get(i);
        final String fullUrl  = (idx < currentFullUrls.size())
                ? currentFullUrls.get(idx) : thumbUrl;

        if (thumbUrl == null || thumbUrl.isBlank()) continue;

        try {
          // Thumbnail: dùng cho strip bên dưới ảnh chính
          Image thumbImg = ImageUtil.loadThumbnail(thumbUrl, 80, 80);
          // Full: dùng cho ảnh chính (chỉ load ảnh đầu tiên ngay lập tức)
          Image mainImg  = (idx == 0) ? ImageUtil.loadImage(fullUrl) : null;

          Platform.runLater(() -> {
            ImageView tv = thumbViews[idx];
            if (tv == null) return;

            tv.setImage(thumbImg);
            tv.setVisible(true);
            tv.setManaged(true);

            // Ảnh đầu tiên → hiển thị lên main view
            if (idx == 0 && imgMainProduct != null && mainImg != null) {
              imgMainProduct.setImage(mainImg);
              imgMainProduct.setPreserveRatio(true);
              imgMainProduct.setSmooth(true);
            }

            // Click thumbnail → load full image tương ứng
            tv.setOnMouseClicked(e -> switchMainImage(fullUrl, thumbViews, idx));

            if (idx == 0) markThumbActive(thumbViews, 0);
          });
        } catch (Exception e) {
          System.err.println("[AuctionRoom] Lỗi load ảnh [" + idx + "]: " + e.getMessage());
        }
      }
    }, "load-product-images").start();
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
            new SimpleStringProperty("MANUAL"));

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
    bidHistoryChart.setLegendVisible(false);
    bidHistoryChart.setCreateSymbols(true);
    if (chartXAxis != null) {
      chartXAxis.setLabel("");
      chartXAxis.setAnimated(false);
    }
    if (chartYAxis != null) {
      chartYAxis.setLabel("");
      // Format Y axis: hiển thị dạng x.xxx.xxx đ
      DecimalFormat vndFmt = new DecimalFormat("#,###");
      chartYAxis.setTickLabelFormatter(new javafx.util.StringConverter<Number>() {
        public String toString(Number n) {
          long val = n.longValue();
          // Format: 2.500.000 đ style (dùng dấu chấm theo kiểu VN)
          String formatted = vndFmt.format(val).replace(",", ".");
          return formatted + " đ";
        }
        public Number fromString(String s) { return 0; }
      });
    }
  }

  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

  private void addChartPoint(BigDecimal price) {
    if (priceSeries == null || price == null) return;
    String timeLabel = LocalDateTime.now().format(TIME_FMT);
    Platform.runLater(() -> {
      priceSeries.getData().add(new XYChart.Data<>(timeLabel, price.doubleValue()));
      updateYAxisBounds();
    });
  }

  /** Vẽ lại toàn bộ biểu đồ từ danh sách lịch sử bid (có timestamp thực). */
  private void populateChartFromHistory(java.util.List<BidTransaction> bids) {
    if (priceSeries == null || bids == null || bids.isEmpty()) return;

    java.util.List<XYChart.Data<String, Number>> points = new java.util.ArrayList<>();
    double minPrice = Double.MAX_VALUE, maxPrice = Double.MIN_VALUE;

    for (BidTransaction b : bids) {
      if (b.getAmount() == null) continue;
      LocalDateTime bidTime = b.getCreatedAt();
      String timeLabel = bidTime != null ? bidTime.format(TIME_FMT) : LocalDateTime.now().format(TIME_FMT);
      double amount = b.getAmount().doubleValue();
      points.add(new XYChart.Data<>(timeLabel, amount));
      if (amount < minPrice) minPrice = amount;
      if (amount > maxPrice) maxPrice = amount;
    }

    // Tính tick unit tròn đẹp (bội số của 50.000, 100.000, 200.000, ...)
    double range   = maxPrice - minPrice;
    double rawTick = range > 0 ? range / 5.0 : maxPrice * 0.05;
    double tickUnit = roundToNiceTickUnit(rawTick);
    if (tickUnit <= 0) tickUnit = 50_000;

    // Trừ thêm 1 tick phía dưới và cộng thêm 1 tick phía trên → điểm đầu/cuối không bị khuất sát mép
    double yLow  = Math.floor(minPrice / tickUnit) * tickUnit - tickUnit;
    if (yLow < 0) yLow = 0;
    double yHigh = Math.ceil(maxPrice / tickUnit) * tickUnit + tickUnit;
    if (yHigh <= yLow + tickUnit) yHigh = yLow + 2 * tickUnit;

    final java.util.List<XYChart.Data<String, Number>> finalPoints = points;
    final double finalLow = yLow, finalHigh = yHigh, finalTick = tickUnit;

    Platform.runLater(() -> {
      if (chartYAxis != null) {
        // Tắt autoRanging để tự kiểm soát bounds — tránh forceZeroInRange
        chartYAxis.setAutoRanging(false);
        chartYAxis.setLowerBound(finalLow);
        chartYAxis.setUpperBound(finalHigh);
        chartYAxis.setTickUnit(finalTick);
      }
      priceSeries.getData().setAll(finalPoints);
    });
  }

  /** Cập nhật Y-axis bounds sau mỗi addChartPoint() realtime. */
  private void updateYAxisBounds() {
    if (chartYAxis == null || priceSeries == null) return;
    double min = priceSeries.getData().stream()
            .mapToDouble(d -> d.getYValue().doubleValue()).min().orElse(0);
    double max = priceSeries.getData().stream()
            .mapToDouble(d -> d.getYValue().doubleValue()).max().orElse(0);

    double range    = max - min;
    double rawTick  = range > 0 ? range / 5.0 : max * 0.05;
    double tickUnit = roundToNiceTickUnit(rawTick);
    if (tickUnit <= 0) tickUnit = 50_000;

    // Trừ thêm 1 tick phía dưới và cộng thêm 1 tick phía trên → điểm đầu/cuối không bị khuất sát mép
    double lowerBound = Math.floor(min / tickUnit) * tickUnit - tickUnit;
    if (lowerBound < 0) lowerBound = 0;
    double upperBound = Math.ceil(max / tickUnit) * tickUnit + tickUnit;
    if (upperBound <= lowerBound + tickUnit) upperBound = lowerBound + 2 * tickUnit;

    chartYAxis.setAutoRanging(false);
    chartYAxis.setLowerBound(lowerBound);
    chartYAxis.setUpperBound(upperBound);
    chartYAxis.setTickUnit(tickUnit);
  }

  /**
   * Làm tròn rawTick thành một giá trị "đẹp" (bội số của 1, 2, 5, 10 × 10^n).
   * Ví dụ: 87_000 → 100_000; 123_000 → 200_000; 45_000 → 50_000
   */
  private double roundToNiceTickUnit(double rawTick) {
    if (rawTick <= 0) return 50_000;
    double magnitude = Math.pow(10, Math.floor(Math.log10(rawTick)));
    double normalized = rawTick / magnitude; // trong [1, 10)
    double niceMult;
    if      (normalized <= 1.5) niceMult = 1;
    else if (normalized <= 3.0) niceMult = 2;
    else if (normalized <= 7.0) niceMult = 5;
    else                        niceMult = 10;
    return niceMult * magnitude;
  }

  private void setupNumberOnlyFields() {
    addNumberOnlyListener(txtBidAmount);
  }

  private void addNumberOnlyListener(TextField field) {
    if (field == null) return;
    field.textProperty().addListener((obs, oldVal, newVal) -> {
      if (newVal != null && !newVal.matches("\\d*")) {
        field.setText(newVal.replaceAll("[^\\d]", ""));
      }
    });
  }

  /** Được gọi bởi ClientSession.balanceListener — cập nhật label số dư trên bất kỳ AuctionRoom nào đang mở */
  private void refreshBalanceLabel() {
    if (ClientSession.getCurrentUser() == null || lblMyBalance == null) return;
    javafx.application.Platform.runLater(() ->
            lblMyBalance.setText(formatMoney(ClientSession.getCurrentUser().getBalance()) + " VNĐ")
    );
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


  private void showAuctionEndedDialog(AuctionUpdateDTO update) {
    int myId = ClientSession.getCurrentUser() != null
            ? ClientSession.getCurrentUser().getId() : -1;
    int winnerId    = update.getHighestBidderId();
    String winner   = update.getHighestBidderUsername();
    BigDecimal price = update.getNewPrice();

    boolean iWon  = (myId != -1 && myId == winnerId);
    boolean noOne = (winnerId == 0 || price == null
            || price.compareTo(BigDecimal.ZERO) == 0);

    // ── Nội dung thay đổi theo kết quả
    String icon, title, sub, btnText, cardColor;
    if (noOne) {
      icon      = "🔨";
      title     = "Phiên đấu giá kết thúc";
      sub       = "Không có người thắng";
      btnText   = "Đóng";
      cardColor = "#37474F";
    } else if (iWon) {
      icon      = "🏆";
      title     = "Chúc mừng! Bạn đã thắng!";
      sub       = String.format("Giá chốt: %s VNĐ", formatMoney(price));
      btnText   = "Tuyệt vời!";
      cardColor = "#1B5E20";
    } else {
      String winnerName = (winner != null && !winner.isBlank()) ? winner : "Người khác";
      icon      = "😔";
      title     = "Phiên đấu giá kết thúc";
      sub       = String.format("Người thắng: %s\nGiá chốt: %s VNĐ",
              winnerName, formatMoney(price));
      btnText   = "Đóng";
      cardColor = "#B71C1C";
    }

    // ── Build dialog layout
    javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(0);
    root.setPrefWidth(360);
    root.setAlignment(javafx.geometry.Pos.CENTER);
    root.setStyle("-fx-background-color: #1a1f2e; -fx-background-radius: 16;");

    // Header màu
    javafx.scene.layout.VBox header = new javafx.scene.layout.VBox(10);
    header.setAlignment(javafx.geometry.Pos.CENTER);
    header.setStyle(String.format(
            "-fx-background-color: %s; -fx-background-radius: 16 16 0 0; -fx-padding: 32 24 24 24;",
            cardColor));

    javafx.scene.control.Label iconLbl = new javafx.scene.control.Label(icon);
    iconLbl.setStyle("-fx-font-size: 52px;");

    javafx.scene.control.Label titleLbl = new javafx.scene.control.Label(title);
    titleLbl.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; " +
            "-fx-text-fill: white; -fx-wrap-text: true; -fx-text-alignment: center;");
    titleLbl.setMaxWidth(300);
    titleLbl.setWrapText(true);

    javafx.scene.control.Label subLbl = new javafx.scene.control.Label(sub);
    subLbl.setStyle("-fx-font-size: 14px; -fx-text-fill: rgba(255,255,255,0.85); " +
            "-fx-wrap-text: true; -fx-text-alignment: center;");
    subLbl.setMaxWidth(300);
    subLbl.setWrapText(true);
    subLbl.setAlignment(javafx.geometry.Pos.CENTER);

    header.getChildren().addAll(iconLbl, titleLbl, subLbl);

    // Thông tin chi tiết bên dưới
    javafx.scene.layout.VBox body = new javafx.scene.layout.VBox(12);
    body.setStyle("-fx-padding: 20 24 8 24;");
    body.setAlignment(javafx.geometry.Pos.CENTER);

    if (!noOne) {
      javafx.scene.layout.HBox priceRow = makeDialogRow("Giá chốt",
              formatMoney(price) + " VNĐ", "#F9A825");
      javafx.scene.layout.HBox winnerRow = makeDialogRow("Người thắng",
              (winner != null && !winner.isBlank()) ? winner : "—", "#81C784");
      body.getChildren().addAll(priceRow, winnerRow);
    }

    javafx.scene.layout.HBox itemRow = makeDialogRow("Sản phẩm",
            currentAuction != null ? currentAuction.getItemName() : "—", "#90CAF9");
    body.getChildren().add(itemRow);

    // Nút đóng
    javafx.scene.control.Button closeBtn = new javafx.scene.control.Button(btnText);
    closeBtn.setMaxWidth(Double.MAX_VALUE);
    closeBtn.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: white; -fx-font-weight: bold; " +
                    "-fx-font-size: 14px; -fx-background-radius: 0 0 16 16; -fx-padding: 14; -fx-cursor: hand;",
            cardColor));

    root.getChildren().addAll(header, body, closeBtn);

    javafx.stage.Stage stage = new javafx.stage.Stage();
    stage.setTitle("Kết thúc phiên đấu giá");
    stage.setScene(new javafx.scene.Scene(root));
    stage.setResizable(false);
    stage.initStyle(javafx.stage.StageStyle.UTILITY);
    closeBtn.setOnAction(e -> {
      stage.close();
      if (lblAuctionTitle.getScene() != null)
        lblAuctionTitle.getScene().getWindow().requestFocus();
    });
    stage.show();
  }

  private javafx.scene.layout.HBox makeDialogRow(String key, String value, String valueColor) {
    javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(12);
    row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    row.setStyle("-fx-padding: 6 0;");

    javafx.scene.control.Label keyLbl = new javafx.scene.control.Label(key + ":");
    keyLbl.setMinWidth(100);
    keyLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #8a9bb0;");

    javafx.scene.control.Label valLbl = new javafx.scene.control.Label(value);
    valLbl.setWrapText(true);
    valLbl.setStyle(String.format(
            "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: %s;", valueColor));

    row.getChildren().addAll(keyLbl, valLbl);
    return row;
  }

  /**
   * Đổi ảnh chính khi click thumbnail.
   * Tải lại full-res ảnh được chọn và cập nhật border active.
   */
  private void switchMainImage(String fullUrl, ImageView[] thumbs, int activeIdx) {
    markThumbActive(thumbs, activeIdx);
    new Thread(() -> {
      try {
        Image fullImg = ImageUtil.loadImage(fullUrl);
        Platform.runLater(() -> {
          if (imgMainProduct != null && fullImg != null) {
            imgMainProduct.setImage(fullImg);
            imgMainProduct.setPreserveRatio(true);
            imgMainProduct.setSmooth(true);
          }
        });
      } catch (Exception e) {
        System.err.println("[AuctionRoom] Lỗi load full image: " + e.getMessage());
      }
    }, "switch-main-image").start();
  }

  /**
   * Đánh dấu thumbnail active bằng cách thêm style border xanh.
   * Các thumbnail còn lại trở về trạng thái bình thường.
   */
  private void markThumbActive(ImageView[] thumbs, int activeIdx) {
    for (int i = 0; i < thumbs.length; i++) {
      if (thumbs[i] == null) continue;
      javafx.scene.layout.StackPane parent =
              (thumbs[i].getParent() instanceof javafx.scene.layout.StackPane)
                      ? (javafx.scene.layout.StackPane) thumbs[i].getParent()
                      : null;
      if (parent != null) {
        if (i == activeIdx) {
          if (!parent.getStyleClass().contains("thumb-active"))
            parent.getStyleClass().add("thumb-active");
        } else {
          parent.getStyleClass().remove("thumb-active");
        }
      }
    }
  }
}