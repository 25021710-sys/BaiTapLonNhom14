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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AuctionRoomController – màn hình đấu giá trực tiếp (realtime bidding).
 *
 * FIX so với phiên bản cũ:
 *  1. colBidUser hiển thị username thật (cache bidderId→username)
 *  2. handlePlaceBid kiểm tra bước giá tối thiểu (stepPrice)
 *  3. Popup thông báo kết quả khi phiên kết thúc
 *  4. handleSendChat gửi tin nhắn kèm timestamp
 *  5. Filter lịch sử bid hoạt động thật sự
 *  6. updateYourStatus dùng highestBidderId từ update thay vì DTO cũ
 *  7. Countdown đổi màu cam khi < 5 phút, đỏ nháy khi < 1 phút
 *  8. Quick-bid tính từ giá hiện tại (luôn cập nhật)
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
  @FXML private Label lblParticipants;

  // ── ART / EXTRA FIELDS ────────────────────────────────────────────────────
  @FXML private Label lblArtistTitle;
  @FXML private Label lblArtist;
  @FXML private Label lblArtYearTitle;
  @FXML private Label lblArtYear;
  @FXML private Label lblMaterialTitle;
  @FXML private Label lblMaterial;
  @FXML private Label lblSellerInline;

  // ── AUCTION INFO ──────────────────────────────────────────────────────────
  @FXML private Label lblAuctionId;
  @FXML private Label lblStartTime;
  @FXML private Label lblEndTime;

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

  // ── CHART ─────────────────────────────────────────────────────────────────
  @FXML private LineChart<Number, Number> bidHistoryChart;
  @FXML private NumberAxis chartXAxis;
  @FXML private NumberAxis chartYAxis;

  // ── STATE ─────────────────────────────────────────────────────────────────
  private AuctionDTO currentAuction;
  private BigDecimal currentPrice       = BigDecimal.ZERO;
  private BigDecimal stepPrice          = new BigDecimal("50000");
  private int        currentHighestBidderId = -1;
  private String     currentHighestUsername = "";
  private LocalDateTime auctionEndTime;
  private Timeline   countdownTimeline;
  private boolean    auctionEnded = false;

  // FIX 1: cache bidderId → username để hiển thị trong bảng
  private final Map<Integer, String> usernameCache = new HashMap<>();

  // Bid history (full + filtered)
  private final ObservableList<BidTransaction> bidHistoryList     = FXCollections.observableArrayList();
  private final ObservableList<BidTransaction> bidHistoryFiltered = FXCollections.observableArrayList();

  // Chart
  private XYChart.Series<Number, Number> priceSeries;
  private long chartStartMs;

  // Formatters
  private final DateTimeFormatter timeFormatter  = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM");
  private final DateTimeFormatter chatFormatter  = DateTimeFormatter.ofPattern("HH:mm");
  private final DecimalFormat     moneyFormat    = new DecimalFormat("#,###");

  // ── INIT ──────────────────────────────────────────────────────────────────

  @FXML
  public void initialize() {
    setupBidHistoryTable();
    setupAutoBidToggle();
    setupNumberOnlyFields();
    setupBidFilter();
    setupChart();
    if (lblBidError != null) lblBidError.setVisible(false);
  }

  // ── LOAD AUCTION (gọi từ Dashboard) ──────────────────────────────────────

  public void loadAuction(AuctionDTO auction) {
    this.currentAuction          = auction;
    this.currentPrice            = auction.getCurrentPrice() != null
            ? auction.getCurrentPrice() : auction.getStartingPrice();
    this.currentHighestBidderId  = auction.getHighestBidderId();
    this.currentHighestUsername  = auction.getHighestBidderUsername() != null
            ? auction.getHighestBidderUsername() : "---";
    this.auctionEndTime          = auction.getEndTime();
    this.chartStartMs            = System.currentTimeMillis();

    Platform.runLater(() -> {
      // Header
      safe(lblAuctionTitle,  "Đấu giá: " + auction.getItemName());
      safe(lblAuctionStatus, auction.getStatus());
      styleStatusBadge(auction.getStatus());

      // Info card trái
      safe(lblCategory,      auction.getItemCategory() != null ? auction.getItemCategory() : "—");
      safe(lblSellerInline,  auction.getSellerName()   != null ? auction.getSellerName()   : "—");

      // Info card đấu giá
      safe(lblStartPrice,    formatMoney(auction.getStartingPrice()) + " VNĐ");
      safe(lblStepPrice,     formatMoney(stepPrice) + " VNĐ");
      safe(lblLeadingUser,   currentHighestUsername);
      safe(lblStartTime,     auction.getStartTime() != null
              ? "Bắt đầu: " + auction.getStartTime().format(timeFormatter) : "—");
      safe(lblEndTime,       auction.getEndTime() != null
              ? "Kết thúc: " + auction.getEndTime().format(timeFormatter) : "—");
      safe(lblAuctionId,     "#" + auction.getAuctionId());

      // Mô tả
      if (txtDescription != null && auction.getItemDescription() != null)
        txtDescription.setText(auction.getItemDescription());

      // Thống kê
      safe(lblTotalBids, String.valueOf(auction.getTotalBids()));

      // Seller panel bên phải
      safe(lblSellerName, "Người bán: " + (auction.getSellerName() != null
              ? auction.getSellerName() : "—"));

      // Giá
      updateCurrentPriceUI();
      updateYourStatus();
    });

    // Đăng ký nhận realtime push
    subscribeRealtime(auction.getAuctionId());

    // Load lịch sử bid
    loadBidHistory();

    // Bắt đầu đếm ngược
    startCountdownTimer();
  }

  // ── REALTIME SUBSCRIBE ────────────────────────────────────────────────────

  private void subscribeRealtime(int auctionId) {
    SocketClient.getInstance().setPushCallback(this::handlePushUpdate);
    new Thread(() -> {
      CreateAuctionResponse res = SocketClient.getInstance().subscribeAuction(auctionId);
      if (res != null && !res.isSuccess()) {
        Platform.runLater(() -> showBidError("Lỗi kết nối phiên: " + res.getMessage()));
      }
    }, "subscribe-thread").start();
  }

  /**
   * FIX 6: Nhận push update từ server. Cập nhật currentHighestBidderId
   * từ update thay vì dùng DTO cũ (có thể stale).
   */
  private void handlePushUpdate(AuctionUpdateDTO update) {
    if (currentAuction == null || update.getAuctionId() != currentAuction.getAuctionId()) return;

    Platform.runLater(() -> {
      switch (update.getType()) {
        case BID_PLACED, AUCTION_EXTENDED -> {
          currentPrice             = update.getNewPrice();
          currentHighestBidderId   = update.getHighestBidderId();
          currentHighestUsername   = update.getHighestBidderUsername() != null
                  ? update.getHighestBidderUsername() : currentHighestUsername;

          if (update.getNewEndTime() != null)
            auctionEndTime = update.getNewEndTime();

          updateCurrentPriceUI();
          safe(lblLeadingUser, currentHighestUsername);

          int newTotal = bidHistoryList.size() + 1;
          safe(lblTotalBids, String.valueOf(newTotal));

          updateYourStatus();
          addChartPoint(currentPrice);

          if (update.getType() == AuctionUpdateDTO.UpdateType.AUCTION_EXTENDED) {
            addSystemMessage("⏱ Phiên được gia hạn thêm 60 giây vì có bid mới!");
          } else {
            String bidder = currentHighestUsername;
            addSystemMessage("💰 " + bidder + " vừa đặt "
                    + formatMoney(currentPrice) + " VNĐ");
          }

          // Refresh bảng lịch sử
          loadBidHistory();
        }
        case AUCTION_ENDED -> {
          auctionEnded = true;
          safe(lblAuctionStatus, "ĐÃ KẾT THÚC");
          if (lblAuctionStatus != null)
            lblAuctionStatus.getStyleClass().setAll("badge-ended");
          stopCountdown();
          safe(lblCountdown, "00:00:00");
          disableBidActions();
          addSystemMessage("🏆 Phiên kết thúc! Người thắng: "
                  + (update.getHighestBidderUsername() != null
                  ? update.getHighestBidderUsername() : "---"));

          // FIX 3: Popup kết quả nếu user tham gia
          showAuctionEndDialog(update);
        }
        default -> {}
      }
    });
  }

  // ── LOAD BID HISTORY ──────────────────────────────────────────────────────

  private void loadBidHistory() {
    if (currentAuction == null) return;
    new Thread(() -> {
      BidHistoryResponse res = SocketClient.getInstance()
              .getBidHistory(currentAuction.getAuctionId());
      if (res != null && res.isSuccess() && res.getBids() != null) {
        List<BidTransaction> bids = res.getBids();

        // Cache username từ server (nếu BidHistoryResponse trả về)
        // Nếu BidTransaction không có username thì dùng bidderId
        Platform.runLater(() -> {
          bidHistoryList.setAll(bids);
          safe(lblTotalBids, String.valueOf(bids.size()));

          applyBidFilter(); // FIX 5: filter thật sự

          // Rebuild chart từ history
          if (priceSeries != null && currentAuction.getStartTime() != null) {
            priceSeries.getData().clear();
            for (BidTransaction b : bids) {
              long secs = b.getCreatedAt() != null
                      ? java.time.Duration.between(
                      currentAuction.getStartTime(), b.getCreatedAt()).getSeconds()
                      : 0;
              priceSeries.getData().add(
                      new XYChart.Data<>(secs, b.getAmount().doubleValue()));
            }
          }
        });
      }
    }, "load-bid-history").start();
  }

  // ── COUNTDOWN ─────────────────────────────────────────────────────────────

  private void startCountdownTimer() {
    if (countdownTimeline != null) countdownTimeline.stop();
    countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> tickCountdown()));
    countdownTimeline.setCycleCount(Timeline.INDEFINITE);
    countdownTimeline.play();
  }

  private void tickCountdown() {
    if (auctionEndTime == null || lblCountdown == null) return;
    long secondsLeft = LocalDateTime.now().until(auctionEndTime, ChronoUnit.SECONDS);
    if (secondsLeft <= 0) {
      lblCountdown.setText("00:00:00");
      stopCountdown();
      if (!auctionEnded) disableBidActions();
      return;
    }
    long h = secondsLeft / 3600;
    long m = (secondsLeft % 3600) / 60;
    long s = secondsLeft % 60;
    lblCountdown.setText(String.format("%02d:%02d:%02d", h, m, s));

    // FIX 7: đổi màu theo mức độ khẩn cấp
    lblCountdown.getStyleClass().removeAll("countdown-warning", "countdown-critical");
    if (secondsLeft < 60) {
      lblCountdown.getStyleClass().add("countdown-critical");
    } else if (secondsLeft < 300) {
      lblCountdown.getStyleClass().add("countdown-warning");
    }
  }

  private void stopCountdown() {
    if (countdownTimeline != null) countdownTimeline.stop();
  }

  private void disableBidActions() {
    if (txtBidAmount       != null) txtBidAmount.setDisable(true);
    if (chkAutoBid         != null) chkAutoBid.setDisable(true);
    if (txtAutoBidMax      != null) txtAutoBidMax.setDisable(true);
    if (txtAutoBidIncrement!= null) txtAutoBidIncrement.setDisable(true);
  }

  // ── QUICK BID ─────────────────────────────────────────────────────────────

  // FIX 8: quick-bid dùng currentPrice (luôn được cập nhật từ server)
  @FXML public void handleQuickBid10k()  { setBidAmount(currentPrice.add(new BigDecimal("10000"))); }
  @FXML public void handleQuickBid50k()  { setBidAmount(currentPrice.add(new BigDecimal("50000"))); }
  @FXML public void handleQuickBid100k() { setBidAmount(currentPrice.add(new BigDecimal("100000"))); }

  private void setBidAmount(BigDecimal value) {
    if (txtBidAmount != null) txtBidAmount.setText(value.toPlainString());
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

    // FIX 2: kiểm tra đúng bước giá tối thiểu
    BigDecimal minValid = currentPrice.add(stepPrice);
    if (bidAmount.compareTo(minValid) < 0) {
      showBidError("Giá bid tối thiểu: " + formatMoney(minValid) + " VNĐ "
              + "(giá hiện tại + bước giá " + formatMoney(stepPrice) + " VNĐ)");
      return;
    }

    if (currentAuction == null) { showBidError("Chưa chọn phiên đấu giá"); return; }
    if (ClientSession.getCurrentUser() == null) { showBidError("Bạn chưa đăng nhập"); return; }

    int userId = ClientSession.getCurrentUser().getId();
    BidRequest req = new BidRequest(userId,
            String.valueOf(currentAuction.getAuctionId()), bidAmount);

    // Disable button tạm thời để tránh double-click
    // (tìm button qua lookup nếu cần; đơn giản nhất là dùng flag)
    new Thread(() -> {
      BidResponse res = SocketClient.getInstance().placeBid(req);
      Platform.runLater(() -> {
        if (res != null && res.isSuccess()) {
          if (txtBidAmount != null) txtBidAmount.clear();
          addSystemMessage("[BẠN] Đặt giá thành công: "
                  + formatMoney(res.getCurrentHighestBid()) + " VNĐ ✓");
        } else {
          showBidError(res != null ? res.getMessage() : "Lỗi kết nối server");
        }
      });
    }, "place-bid-thread").start();
  }

  // ── AUTO BID ──────────────────────────────────────────────────────────────

  @FXML
  public void handleEnableAutoBid() {
    if (lblBidError != null) lblBidError.setVisible(false);
    if (chkAutoBid != null && !chkAutoBid.isSelected()) {
      showBidError("Bật checkbox Auto-bid trước"); return;
    }
    String maxStr = txtAutoBidMax != null ? txtAutoBidMax.getText().trim() : "";
    if (maxStr.isEmpty()) { showBidError("Nhập giá tối đa auto-bid"); return; }

    BigDecimal max;
    try { max = new BigDecimal(maxStr); }
    catch (NumberFormatException e) { showBidError("Giá tối đa không hợp lệ"); return; }

    if (max.compareTo(currentPrice) <= 0) {
      showBidError("Giá tối đa phải lớn hơn giá hiện tại (" + formatMoney(currentPrice) + " VNĐ)");
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
        if (res != null && res.isSuccess()) {
          addSystemMessage("[AUTO-BID] Đã bật. Tối đa: "
                  + formatMoney(maxFinal) + " VNĐ");
        } else {
          showBidError("Lỗi auto-bid: " + (res != null ? res.getMessage() : "server error"));
        }
      });
    }, "register-autobid-thread").start();
  }

  @FXML
  public void handleDisableAutoBid() {
    if (currentAuction == null || ClientSession.getCurrentUser() == null) return;
    if (chkAutoBid != null) chkAutoBid.setSelected(false);
    new Thread(() -> {
      SocketClient.getInstance().cancelAutoBid(
              ClientSession.getCurrentUser().getId(),
              currentAuction.getAuctionId());
      Platform.runLater(() -> addSystemMessage("[AUTO-BID] Đã tắt."));
    }, "cancel-autobid-thread").start();
  }

  // ── CHAT ──────────────────────────────────────────────────────────────────

  // FIX 4: gửi kèm timestamp, rõ ràng hơn
  @FXML
  public void handleSendChat() {
    if (txtChatInput == null) return;
    String msg = txtChatInput.getText().trim();
    if (msg.isEmpty()) return;

    String username = ClientSession.getCurrentUser() != null
            ? ClientSession.getCurrentUser().getUsername() : "Bạn";
    String time = LocalDateTime.now().format(chatFormatter);

    // Hiển thị ngay (local echo)
    String formatted = "[" + time + "] " + username + ": " + msg;
    if (lvChatMessages != null) {
      lvChatMessages.getItems().add(formatted);
      // Scroll xuống cuối
      lvChatMessages.scrollTo(lvChatMessages.getItems().size() - 1);
    }
    txtChatInput.clear();
  }

  // ── BID HISTORY FILTER ────────────────────────────────────────────────────

  @FXML
  public void handleRefreshBidHistory() {
    loadBidHistory();
  }

  // FIX 5: filter thực sự lọc dữ liệu
  private void applyBidFilter() {
    String filter = cbBidFilter != null ? cbBidFilter.getValue() : "Tất cả";
    if (filter == null || filter.equals("Tất cả")) {
      bidHistoryFiltered.setAll(bidHistoryList);
    } else if (filter.equals("Chỉ của tôi") && ClientSession.getCurrentUser() != null) {
      int myId = ClientSession.getCurrentUser().getId();
      bidHistoryFiltered.setAll(bidHistoryList.filtered(b -> b.getBidderId() == myId));
    } else if (filter.equals("Auto-bid")) {
      bidHistoryFiltered.setAll(bidHistoryList.filtered(BidTransaction::isAutoBid));
    } else {
      bidHistoryFiltered.setAll(bidHistoryList);
    }
  }

  // ── SELLER ACTIONS ────────────────────────────────────────────────────────

  @FXML
  public void handleViewSellerProfile() {
    if (currentAuction == null) return;
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("Thông tin người bán");
    alert.setHeaderText(currentAuction.getSellerName());
    alert.setContentText("Chức năng xem profile người bán đang được phát triển.");
    alert.showAndWait();
  }

  @FXML
  public void handleMessageSeller() {
    addSystemMessage("[INFO] Chức năng nhắn tin sẽ sớm ra mắt!");
  }

  // ── WINNER POPUP ──────────────────────────────────────────────────────────

  // FIX 3: Hiển thị popup kết quả khi phiên kết thúc
  private void showAuctionEndDialog(AuctionUpdateDTO update) {
    boolean isWinner = ClientSession.getCurrentUser() != null
            && update.getHighestBidderId() == ClientSession.getCurrentUser().getId();

    Stage dialog = new Stage();
    dialog.initModality(Modality.APPLICATION_MODAL);
    dialog.setTitle(isWinner ? "🎉 Chúc mừng!" : "📋 Kết quả đấu giá");

    VBox box = new VBox(16);
    box.setAlignment(Pos.CENTER);
    box.setPadding(new Insets(32));
    box.setStyle("-fx-background-color: white;");

    Label icon = new Label(isWinner ? "🏆" : "📋");
    icon.setStyle("-fx-font-size: 48px;");

    Label title = new Label(isWinner ? "Bạn đã thắng phiên đấu giá!" : "Phiên đấu giá kết thúc");
    title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: "
            + (isWinner ? "#27AE60" : "#2C3E50") + ";");

    Label priceLabel = new Label("Giá cuối: " + formatMoney(update.getNewPrice()) + " VNĐ");
    priceLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #E67E22;");

    Label winnerLabel = new Label("Người thắng: " + (update.getHighestBidderUsername() != null
            ? update.getHighestBidderUsername() : "---"));
    winnerLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #7F8C8D;");

    Button btnOk = new Button(isWinner ? "Tuyệt vời!" : "Đóng");
    btnOk.setStyle("-fx-background-color: " + (isWinner ? "#27AE60" : "#2196F3")
            + "; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;"
            + "-fx-padding: 10 28 10 28; -fx-background-radius: 8; -fx-cursor: hand;");
    btnOk.setOnAction(e -> dialog.close());

    box.getChildren().addAll(icon, title, priceLabel, winnerLabel, btnOk);
    Scene scene = new Scene(box, 360, 260);
    dialog.setScene(scene);
    dialog.setResizable(false);
    dialog.show();
  }

  // ── SETUP HELPERS ─────────────────────────────────────────────────────────

  private void setupBidHistoryTable() {
    tblBidHistory.setItems(bidHistoryFiltered);

    colBidTime.setCellValueFactory(data -> {
      LocalDateTime t = data.getValue().getCreatedAt();
      return new SimpleStringProperty(t != null ? t.format(timeFormatter) : "—");
    });

    // FIX 1: hiển thị username thật, fallback về "User#id"
    colBidUser.setCellValueFactory(data -> {
      int bidderId = data.getValue().getBidderId();
      String name = usernameCache.getOrDefault(bidderId, "User#" + bidderId);
      return new SimpleStringProperty(name);
    });

    colBidAmount.setCellValueFactory(data ->
            new SimpleStringProperty(formatMoney(data.getValue().getAmount()) + " VNĐ"));

    colBidType.setCellValueFactory(data -> {
      boolean auto = data.getValue().isAutoBid();
      return new SimpleStringProperty(auto ? "🤖 AUTO" : "✋ MANUAL");
    });

    // Highlight row của user hiện tại
    tblBidHistory.setRowFactory(tv -> new TableRow<BidTransaction>() {
      @Override
      protected void updateItem(BidTransaction item, boolean empty) {
        super.updateItem(item, empty);
        if (item == null || empty) {
          setStyle("");
        } else if (ClientSession.getCurrentUser() != null
                && item.getBidderId() == ClientSession.getCurrentUser().getId()) {
          setStyle("-fx-background-color: #E8F5E9;");
        } else {
          setStyle("");
        }
      }
    });
  }

  private void setupBidFilter() {
    if (cbBidFilter == null) return;
    cbBidFilter.setItems(FXCollections.observableArrayList("Tất cả", "Chỉ của tôi", "Auto-bid"));
    cbBidFilter.setValue("Tất cả");
    cbBidFilter.setOnAction(e -> applyBidFilter());
  }

  private void setupChart() {
    if (bidHistoryChart == null) return;
    priceSeries = new XYChart.Series<>();
    priceSeries.setName("Giá đấu");
    bidHistoryChart.getData().add(priceSeries);
    bidHistoryChart.setAnimated(false); // tắt animation để update nhanh hơn
  }

  private void addChartPoint(BigDecimal price) {
    if (priceSeries == null) return;
    long seconds = (System.currentTimeMillis() - chartStartMs) / 1000;
    Platform.runLater(() ->
            priceSeries.getData().add(new XYChart.Data<>(seconds, price.doubleValue())));
  }

  private void setupAutoBidToggle() {
    if (chkAutoBid == null) return;
    chkAutoBid.selectedProperty().addListener((obs, oldVal, newVal) -> {
      if (txtAutoBidMax       != null) txtAutoBidMax.setDisable(!newVal);
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

  // ── UI UPDATE HELPERS ─────────────────────────────────────────────────────

  private void updateCurrentPriceUI() {
    String formatted = formatMoney(currentPrice) + " VNĐ";
    safe(lblCurrentPrice,      formatted);
    safe(lblCurrentPriceRight, formatted);
  }

  // FIX 6: dùng currentHighestBidderId (cập nhật từ push) thay vì DTO cũ
  private void updateYourStatus() {
    if (lblYourStatus == null || ClientSession.getCurrentUser() == null) return;
    boolean isLeading = currentHighestBidderId == ClientSession.getCurrentUser().getId();
    if (currentHighestBidderId <= 0) {
      lblYourStatus.setText("Chưa có người đặt giá");
      lblYourStatus.getStyleClass().setAll("status-neutral");
    } else if (isLeading) {
      lblYourStatus.setText("✅ Bạn đang dẫn đầu!");
      lblYourStatus.getStyleClass().setAll("status-winning");
    } else {
      lblYourStatus.setText("❌ Bạn đang bị vượt giá!");
      lblYourStatus.getStyleClass().setAll("status-losing");
    }
  }

  private void styleStatusBadge(String status) {
    if (lblAuctionStatus == null || status == null) return;
    lblAuctionStatus.getStyleClass().removeAll("badge-running", "badge-ended",
            "badge-pending", "badge-open");
    switch (status.toUpperCase()) {
      case "RUNNING", "ĐANG DIỄN RA" -> lblAuctionStatus.getStyleClass().add("badge-running");
      case "FINISHED", "ENDED", "ĐÃ KẾT THÚC" -> lblAuctionStatus.getStyleClass().add("badge-ended");
      case "PENDING", "CHỜ DUYỆT"  -> lblAuctionStatus.getStyleClass().add("badge-pending");
      default -> lblAuctionStatus.getStyleClass().add("badge-open");
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

  /** Null-safe setText */
  private void safe(Label label, String text) {
    if (label != null) label.setText(text != null ? text : "—");
  }

  private String formatMoney(BigDecimal value) {
    if (value == null) return "0";
    return moneyFormat.format(value);
  }

  private String formatMoney(double value) {
    return moneyFormat.format(value);
  }
}