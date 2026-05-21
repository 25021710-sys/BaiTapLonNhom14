package com.auction.client.controller;

import com.auction.client.network.SocketClient;
import com.auction.common.request.CreateAuctionRequest;
import com.auction.common.response.CreateAuctionResponse;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class CreateAuctionController {

  // ======================
  // BASIC FIELDS
  // ======================
  @FXML private TextField txtName;
  @FXML private ComboBox<String> cbCategory;
  @FXML private TextArea txtDescription;
  @FXML private TextField txtStartPrice;

  // ======================
  // DATE + TIME (ComboBox)
  // ======================
  @FXML private DatePicker dpStartDate;
  @FXML private ComboBox<Integer> cbStartHour;
  @FXML private ComboBox<Integer> cbStartMinute;

  @FXML private DatePicker dpEndDate;
  @FXML private ComboBox<Integer> cbEndHour;
  @FXML private ComboBox<Integer> cbEndMinute;

  // ======================
  // ERROR LABEL
  // ======================
  @FXML private Label lblError;

  // ======================
  // IMAGES
  // ======================
  @FXML private ListView<File> lvImages;
  @FXML private ImageView imgPreview;

  /** Danh sách file ảnh người dùng đã chọn (tối đa 4 ảnh). */
  private final ObservableList<File> imageFiles = FXCollections.observableArrayList();
  private static final int MAX_IMAGES = 4;

  // ======================
  // INIT
  // ======================
  @FXML
  public void initialize() {

    cbCategory.getItems().addAll("ART", "ELECTRONICS", "VEHICLE");

    for (int h = 0; h <= 23; h++) { cbStartHour.getItems().add(h); cbEndHour.getItems().add(h); }
    for (int m = 0; m <= 59; m++) { cbStartMinute.getItems().add(m); cbEndMinute.getItems().add(m); }

    cbStartHour.setValue(0);   cbStartMinute.setValue(0);
    cbEndHour.setValue(0);     cbEndMinute.setValue(0);

    lvImages.setItems(imageFiles);

    // Hiển thị tên file ngắn gọn trong ListView
    lvImages.setCellFactory(lv -> new ListCell<File>() {
      @Override
      protected void updateItem(File f, boolean empty) {
        super.updateItem(f, empty);
        if (empty || f == null) { setText(null); }
        else {
          // Hiện thứ tự + tên file: "1. product.jpg"
          int idx = lv.getItems().indexOf(f);
          setText((idx == 0 ? "⭐ " : (idx + 1) + ". ") + f.getName());
        }
      }
    });

    // Preview khi chọn ảnh trong list
    lvImages.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
      if (newVal != null) {
        imgPreview.setImage(new Image(newVal.toURI().toString()));
      }
    });
  }

  // ======================
  // IMAGE HANDLING
  // ======================

  @FXML
  public void handleChooseImages() {
    int remaining = MAX_IMAGES - imageFiles.size();
    if (remaining <= 0) {
      showError("Đã đạt tối đa " + MAX_IMAGES + " ảnh. Xóa bớt để thêm ảnh mới.");
      return;
    }

    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Chọn ảnh sản phẩm (tối đa " + MAX_IMAGES + " ảnh)");
    fileChooser.getExtensionFilters().add(
        new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
    );

    List<File> selected = fileChooser.showOpenMultipleDialog(txtName.getScene().getWindow());
    if (selected == null || selected.isEmpty()) return;

    int added = 0;
    for (File f : selected) {
      if (imageFiles.size() >= MAX_IMAGES) break;
      if (!imageFiles.contains(f)) {
        imageFiles.add(f);
        added++;
      }
    }

    if (added == 0) return;

    // Auto-preview ảnh mới thêm đầu tiên nếu chưa có gì được chọn
    if (lvImages.getSelectionModel().getSelectedItem() == null) {
      lvImages.getSelectionModel().select(0);
    }

    // Xóa thông báo lỗi cũ nếu đã chọn ảnh hợp lệ
    if (lblError.isVisible()) lblError.setVisible(false);

    // Cập nhật lại cell factory để hiện đúng thứ tự
    lvImages.refresh();
  }

  @FXML
  public void handleRemoveSelectedImage() {
    File selected = lvImages.getSelectionModel().getSelectedItem();
    if (selected == null) return;

    imageFiles.remove(selected);

    if (imageFiles.isEmpty()) {
      imgPreview.setImage(null);
    } else {
      // Chọn ảnh kế tiếp (hoặc ảnh cuối nếu xóa ảnh cuối)
      int newIdx = Math.min(lvImages.getSelectionModel().getSelectedIndex(), imageFiles.size() - 1);
      lvImages.getSelectionModel().select(Math.max(0, newIdx));
    }

    lvImages.refresh(); // Cập nhật lại nhãn thứ tự
  }

  // ======================
  // CREATE AUCTION
  // ======================

  @FXML
  public void handleCreateAuction() {
    lblError.setVisible(false);

    // Validate name
    if (isEmpty(txtName)) { showError("Vui lòng nhập tên sản phẩm"); return; }

    // Validate category
    if (cbCategory.getValue() == null) { showError("Vui lòng chọn danh mục"); return; }

    // Validate description
    if (txtDescription.getText().trim().isEmpty()) { showError("Vui lòng nhập mô tả sản phẩm"); return; }

    // Validate price
    if (isEmpty(txtStartPrice)) { showError("Vui lòng nhập giá khởi điểm"); return; }
    double startPrice;
    try {
      startPrice = Double.parseDouble(txtStartPrice.getText().trim());
      if (startPrice <= 0) { showError("Giá khởi điểm phải lớn hơn 0"); return; }
    } catch (NumberFormatException e) { showError("Giá khởi điểm phải là số hợp lệ"); return; }

    // Validate time
    if (dpStartDate.getValue() == null || dpEndDate.getValue() == null) {
      showError("Vui lòng chọn ngày bắt đầu và ngày kết thúc"); return;
    }
    if (cbStartHour.getValue() == null || cbStartMinute.getValue() == null
        || cbEndHour.getValue() == null || cbEndMinute.getValue() == null) {
      showError("Vui lòng chọn giờ và phút cho thời gian"); return;
    }

    LocalDateTime startTime = buildDateTime(dpStartDate.getValue(), cbStartHour.getValue(), cbStartMinute.getValue());
    LocalDateTime endTime   = buildDateTime(dpEndDate.getValue(),   cbEndHour.getValue(),   cbEndMinute.getValue());

    if (!endTime.isAfter(startTime)) { showError("Thời gian kết thúc phải sau thời gian bắt đầu"); return; }
    if (startTime.isBefore(LocalDateTime.now())) { showError("Thời gian bắt đầu phải >= thời gian hiện tại"); return; }

    // Validate images
    if (imageFiles.isEmpty()) { showError("Vui lòng chọn ít nhất 1 ảnh sản phẩm"); return; }

    // Build request
    CreateAuctionRequest req = new CreateAuctionRequest();
    req.setItemName(txtName.getText().trim());
    req.setItemDescription(txtDescription.getText().trim());
    req.setItemCategory(cbCategory.getValue());
    req.setStartingPrice(BigDecimal.valueOf(startPrice));
    req.setStartTime(startTime);
    req.setEndTime(endTime);

    // ── Encode TẤT CẢ ảnh thành Base64 và gửi lên server ──────────────────
    List<String> imagesBase64 = new ArrayList<>();
    for (File imgFile : imageFiles) {
      try {
        byte[] bytes = java.nio.file.Files.readAllBytes(imgFile.toPath());
        imagesBase64.add(java.util.Base64.getEncoder().encodeToString(bytes));
      } catch (IOException e) {
        showError("Lỗi đọc file ảnh: " + imgFile.getName());
        return;
      }
    }
    req.setImagesBase64(imagesBase64);

    // Gửi request
    CreateAuctionResponse response = SocketClient.getInstance().createAuction(req);

    if (response == null || !response.isSuccess()) {
      showError(response != null ? response.getMessage() : "Lỗi kết nối server");
      return;
    }

    // Success
    System.out.println("===== CREATE AUCTION SUCCESS =====");
    System.out.println("Name: "        + txtName.getText());
    System.out.println("Category: "    + cbCategory.getValue());
    System.out.println("Start Price: " + startPrice);
    System.out.println("Start Time: "  + startTime);
    System.out.println("End Time: "    + endTime);
    System.out.println("Images: "      + imageFiles.size());
    System.out.println("==================================");

    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("Tạo phiên đấu giá");
    alert.setHeaderText("Phiên đấu giá đã được gửi đi");
    alert.setContentText("Thông tin phiên đấu giá sẽ được gửi đến admin.\nVui lòng chờ để được phê duyệt.");
    alert.showAndWait();
  }

  // ======================
  // HELPERS
  // ======================

  private LocalDateTime buildDateTime(LocalDate date, Integer hour, Integer minute) {
    return LocalDateTime.of(date, LocalTime.of(hour, minute));
  }

  private boolean isEmpty(TextField tf) {
    return tf.getText() == null || tf.getText().trim().isEmpty();
  }

  private void showError(String msg) {
    lblError.setText(msg);
    lblError.setVisible(true);
  }

  @FXML
  public void handleCategoryChange() {
    String category = cbCategory.getValue();
    if (category != null) System.out.println("Đã chọn category: " + category);
  }
}