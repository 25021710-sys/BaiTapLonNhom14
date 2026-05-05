package com.auction.client.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class AuctionRoomController {

  // =========================
  // TOP INFO
  // =========================
  @FXML private Label lblAuctionTitle;
  @FXML private Label lblAuctionStatus;

  @FXML private Label lblCategory;
  @FXML private Label lblStartPrice;
  @FXML private Label lblCurrentPrice;
  @FXML private Label lblTotalBids;
  @FXML private Label lblLeadingUser;
  @FXML private Label lblStepPrice;

  @FXML private TextArea txtDescription;

  // =========================
  // IMAGES
  // =========================
  @FXML private ImageView imgMainProduct;
  @FXML private ImageView imgThumb1;
  @FXML private ImageView imgThumb2;
  @FXML private ImageView imgThumb3;
  @FXML private ImageView imgThumb4;

  // =========================
  // BID HISTORY TABLE
  // =========================
  @FXML private TableView<Object> tblBidHistory;
  @FXML private TableColumn<Object, String> colBidTime;
  @FXML private TableColumn<Object, String> colBidUser;
  @FXML private TableColumn<Object, String> colBidAmount;
  @FXML private TableColumn<Object, String> colBidType;

  @FXML private ComboBox<String> cbBidFilter;

  // =========================
  // CHAT
  // =========================
  @FXML private ListView<String> lvChatMessages;
  @FXML private TextField txtChatInput;

  // =========================
  // RIGHT PANEL
  // =========================
  @FXML private Label lblCountdown;
  @FXML private Label lblCurrentPriceRight;
  @FXML private Label lblYourStatus;

  @FXML private TextField txtBidAmount;
  @FXML private Label lblBidError;

  // AUTO BID
  @FXML private CheckBox chkAutoBid;
  @FXML private TextField txtAutoBidMax;
  @FXML private Button btnEnableAutoBid; // NOTE: nếu bạn muốn bind nút, phải thêm fx:id vào FXML

  // SELLER INFO
  @FXML private Label lblSellerName;
  @FXML private Label lblSellerRating;
  @FXML private Label lblSellerProducts;

  // AUCTION INFO
  @FXML private Label lblAuctionId;
  @FXML private Label lblStartTime;
  @FXML private Label lblEndTime;
  @FXML private Label lblParticipants;

  // =========================
  // DEMO DATA
  // =========================
  private double currentPrice = 2350000;
  private double stepPrice = 50000;

  private LocalDateTime auctionEndTime;
  private Timeline countdownTimeline;

  private final DecimalFormat moneyFormat = new DecimalFormat("#,###");

  // =========================
  // INIT
  // =========================
  @FXML
  public void initialize() {

    // demo end time: 15 minutes from now
    auctionEndTime = LocalDateTime.now().plusMinutes(15);

    // setup filter
    cbBidFilter.setItems(FXCollections.observableArrayList("Tất cả", "Chỉ của tôi"));
    cbBidFilter.setValue("Tất cả");

    // demo chat
    lvChatMessages.getItems().addAll(
        "[seller] Chào mọi người, đấu giá vui vẻ nhé!",
        "[userA] Tranh này còn giấy chứng nhận không?",
        "[seller] Có giấy chứng nhận đầy đủ."
    );

    // demo product info
    lblAuctionTitle.setText("Đấu giá: Tranh sơn dầu cổ điển");
    lblAuctionStatus.setText("ĐANG DIỄN RA");
    lblCategory.setText("Art");
    lblStartPrice.setText(formatMoney(1000000) + " VNĐ");
    lblStepPrice.setText(formatMoney(stepPrice) + " VNĐ");
    lblTotalBids.setText("35");
    lblLeadingUser.setText("user_abc***");
    txtDescription.setText("Tranh sơn dầu cổ điển, bảo quản tốt, phù hợp trưng bày.");

    updateCurrentPriceUI();

    // demo auction info
    lblAuctionId.setText("Auction ID: #AUC00123");
    lblStartTime.setText("Bắt đầu: 05/05/2026 07:00");
    lblEndTime.setText("Kết thúc: 05/05/2026 08:00");
    lblParticipants.setText("Người tham gia: 18");

    // demo seller info
    lblSellerName.setText("Seller: duyanh_store");
    lblSellerRating.setText("Rating: ⭐ 4.8/5");
    lblSellerProducts.setText("Sản phẩm đã bán: 120");

    // demo images (if you want, replace with real URL/path later)
    setDemoImages();

    // auto bid toggle
    chkAutoBid.selectedProperty().addListener((obs, oldVal, newVal) -> {
      txtAutoBidMax.setDisable(!newVal);
    });

    // start countdown
    startCountdownTimer();

    // default status
    lblYourStatus.setText("Bạn đang bị vượt giá!");
    lblBidError.setVisible(false);

    // validate input (optional)
    addNumberOnlyListener(txtBidAmount);
    addNumberOnlyListener(txtAutoBidMax);
  }

  // =========================
  // COUNTDOWN TIMER
  // =========================
  private void startCountdownTimer() {

    countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
      long secondsLeft = LocalDateTime.now().until(auctionEndTime, ChronoUnit.SECONDS);

      if (secondsLeft <= 0) {
        lblCountdown.setText("00:00:00");
        lblAuctionStatus.setText("ĐÃ KẾT THÚC");
        lblYourStatus.setText("Phiên đấu giá đã kết thúc");
        stopCountdown();
        disableBidActions();
        return;
      }

      long hours = secondsLeft / 3600;
      long minutes = (secondsLeft % 3600) / 60;
      long seconds = secondsLeft % 60;

      lblCountdown.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));

      // warning when < 1 minute
      if (secondsLeft < 60) {
        lblCountdown.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: red;");
      }
    }));

    countdownTimeline.setCycleCount(Timeline.INDEFINITE);
    countdownTimeline.play();
  }

  private void stopCountdown() {
    if (countdownTimeline != null) {
      countdownTimeline.stop();
    }
  }

  private void disableBidActions() {
    txtBidAmount.setDisable(true);
    chkAutoBid.setDisable(true);
    txtAutoBidMax.setDisable(true);
  }

  // =========================
  // QUICK BID BUTTONS
  // =========================
  @FXML
  public void handleQuickBid10k() {
    setBidAmount(currentPrice + 10000);
  }

  @FXML
  public void handleQuickBid50k() {
    setBidAmount(currentPrice + 50000);
  }

  @FXML
  public void handleQuickBid100k() {
    setBidAmount(currentPrice + 100000);
  }

  private void setBidAmount(double value) {
    txtBidAmount.setText(String.valueOf((long) value));
  }

  // =========================
  // PLACE BID
  // =========================
  @FXML
  public void handlePlaceBid() {

    lblBidError.setVisible(false);

    if (txtBidAmount.getText().trim().isEmpty()) {
      showBidError("Vui lòng nhập giá bid");
      return;
    }

    double bidAmount;
    try {
      bidAmount = Double.parseDouble(txtBidAmount.getText().trim());
    } catch (NumberFormatException e) {
      showBidError("Giá bid không hợp lệ");
      return;
    }

    double minValidBid = currentPrice + stepPrice;

    if (bidAmount < minValidBid) {
      showBidError("Giá bid phải >= " + formatMoney(minValidBid) + " VNĐ");
      return;
    }

    // TODO: send bid to server
    // SocketClient.getInstance().placeBid(...)

    // DEMO update
    currentPrice = bidAmount;
    lblLeadingUser.setText("Bạn");

    int totalBids = Integer.parseInt(lblTotalBids.getText());
    lblTotalBids.setText(String.valueOf(totalBids + 1));

    lblYourStatus.setText("Bạn đang dẫn đầu!");
    lblYourStatus.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");

    updateCurrentPriceUI();
    txtBidAmount.clear();

    // add to chat as notification
    lvChatMessages.getItems().add("[SYSTEM] Bạn đã đặt giá: " + formatMoney(currentPrice) + " VNĐ");
  }

  private void showBidError(String msg) {
    lblBidError.setText(msg);
    lblBidError.setVisible(true);
  }

  // =========================
  // AUTO BID
  // =========================
  @FXML
  public void handleEnableAutoBid() {

    lblBidError.setVisible(false);

    if (!chkAutoBid.isSelected()) {
      showBidError("Bạn chưa bật Auto-bid");
      return;
    }

    if (txtAutoBidMax.getText().trim().isEmpty()) {
      showBidError("Vui lòng nhập giá tối đa Auto-bid");
      return;
    }

    double max;
    try {
      max = Double.parseDouble(txtAutoBidMax.getText().trim());
    } catch (NumberFormatException e) {
      showBidError("Giá tối đa không hợp lệ");
      return;
    }

    if (max <= currentPrice) {
      showBidError("Giá tối đa phải lớn hơn giá hiện tại");
      return;
    }

    // TODO: send auto bid config to server
    // SocketClient.getInstance().enableAutoBid(...)

    lvChatMessages.getItems().add("[SYSTEM] Auto-bid đã bật với max: " + formatMoney(max) + " VNĐ");
  }

  // =========================
  // CHAT
  // =========================
  @FXML
  public void handleSendChat() {

    String msg = txtChatInput.getText().trim();
    if (msg.isEmpty()) return;

    // TODO: send to server
    // SocketClient.getInstance().sendChat(...)

    lvChatMessages.getItems().add("[Bạn] " + msg);
    txtChatInput.clear();
  }

  // =========================
  // BID HISTORY REFRESH
  // =========================
  @FXML
  public void handleRefreshBidHistory() {
    // TODO: load bid history from server
    lvChatMessages.getItems().add("[SYSTEM] Refresh bid history...");
  }

  // =========================
  // SELLER ACTIONS
  // =========================
  @FXML
  public void handleViewSellerProfile() {
    lvChatMessages.getItems().add("[SYSTEM] Xem profile người bán...");
    // TODO: open seller profile view
  }

  @FXML
  public void handleMessageSeller() {
    lvChatMessages.getItems().add("[SYSTEM] Mở chat riêng với người bán...");
    // TODO: open private message view
  }

  // =========================
  // UI HELPERS
  // =========================
  private void updateCurrentPriceUI() {
    lblCurrentPrice.setText(formatMoney(currentPrice) + " VNĐ");
    lblCurrentPriceRight.setText(formatMoney(currentPrice) + " VNĐ");
  }

  private String formatMoney(double value) {
    return moneyFormat.format(value);
  }

  private void addNumberOnlyListener(TextField field) {
    ChangeListener<String> listener = (obs, oldVal, newVal) -> {
      if (newVal == null) return;
      if (!newVal.matches("\\d*")) {
        field.setText(newVal.replaceAll("[^\\d]", ""));
      }
    };
    field.textProperty().addListener(listener);
  }

  // =========================
  // DEMO IMAGES
  // =========================
  private void setDemoImages() {
    try {
      // Nếu bạn có ảnh thật trong resources, thay bằng đường dẫn thật
      // Ví dụ: new Image(getClass().getResource("/images/demo.png").toExternalForm());

      Image demo = new Image("https://via.placeholder.com/600x400.png?text=Product+Image");

      imgMainProduct.setImage(demo);
      imgThumb1.setImage(demo);
      imgThumb2.setImage(demo);
      imgThumb3.setImage(demo);
      imgThumb4.setImage(demo);

      // click thumbnail to change main
      imgThumb1.setOnMouseClicked(e -> imgMainProduct.setImage(imgThumb1.getImage()));
      imgThumb2.setOnMouseClicked(e -> imgMainProduct.setImage(imgThumb2.getImage()));
      imgThumb3.setOnMouseClicked(e -> imgMainProduct.setImage(imgThumb3.getImage()));
      imgThumb4.setOnMouseClicked(e -> imgMainProduct.setImage(imgThumb4.getImage()));

    } catch (Exception e) {
      System.out.println("Không load được demo image");
    }
  }
}