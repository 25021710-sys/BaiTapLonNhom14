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

  private final ObservableList<File> imageFiles = FXCollections.observableArrayList();
  private String imageBase64; // thêm field này

  // ======================
  // INIT
  // ======================
  @FXML
  public void initialize() {

    // Category options
    cbCategory.getItems().addAll(
            "ART",
            "ELECTRONICS",
            "VEHICLE",
            "OTHER"
    );

    // Setup hours
    for (int h = 0; h <= 23; h++) {
      cbStartHour.getItems().add(h);
      cbEndHour.getItems().add(h);
    }

    // Setup minutes
    for (int m = 0; m <= 59; m++) {
      cbStartMinute.getItems().add(m);
      cbEndMinute.getItems().add(m);
    }

    // Default time values
    cbStartHour.setValue(0);
    cbStartMinute.setValue(0);

    cbEndHour.setValue(0);
    cbEndMinute.setValue(0);

    // Images list
    lvImages.setItems(imageFiles);

    // Show preview when select image
    lvImages.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
      if (newVal != null) {
        imgPreview.setImage(new Image(newVal.toURI().toString()));
      }
    });
  }

  // ======================
  // CATEGORY CHANGE
  // ======================

  private void hideFields(VBox box) {
    box.setVisible(false);
    box.setManaged(false);
  }

  private void showFields(VBox box) {
    box.setVisible(true);
    box.setManaged(true);
  }

  // ======================
  // IMAGE HANDLING
  // ======================
  @FXML
  public void handleChooseImages() {

    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Chọn ảnh sản phẩm");

    fileChooser.getExtensionFilters().add(
        new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
    );

    var selectedFiles = fileChooser.showOpenMultipleDialog(txtName.getScene().getWindow());

    if (selectedFiles != null) {

      for (File f : selectedFiles) {
        if (!imageFiles.contains(f)) {
          imageFiles.add(f);
        }
      }

      // auto preview first image
      if (!imageFiles.isEmpty() && lvImages.getSelectionModel().getSelectedItem() == null) {
        lvImages.getSelectionModel().select(0);
      }
    }
  }

  @FXML
  public void handleRemoveSelectedImage() {

    File selected = lvImages.getSelectionModel().getSelectedItem();

    if (selected != null) {
      imageFiles.remove(selected);

      if (imageFiles.isEmpty()) {
        imgPreview.setImage(null);
      } else {
        lvImages.getSelectionModel().select(0);
      }
    }
  }

  // ======================
  // CREATE AUCTION
  // ======================
  @FXML
  public void handleCreateAuction() {

    lblError.setVisible(false);

    // BASIC REQUIRED
    if (isEmpty(txtName)) {
      showError("Vui lòng nhập tên sản phẩm");
      return;
    }

    if (cbCategory.getValue() == null) {
      showError("Vui lòng chọn danh mục");
      return;
    }

    if (txtDescription.getText().trim().isEmpty()) {
      showError("Vui lòng nhập mô tả sản phẩm");
      return;
    }

    if (isEmpty(txtStartPrice)) {
      showError("Vui lòng nhập giá khởi điểm");
      return;
    }

      // PRICE CHECK
    double startPrice;
    try {
      startPrice = Double.parseDouble(txtStartPrice.getText().trim());
      if (startPrice <= 0) {
        showError("Giá khởi điểm phải lớn hơn 0");
        return;
      }
    } catch (NumberFormatException e) {
      showError("Giá khởi điểm phải là số hợp lệ");
      return;
    }

    // TIME REQUIRED
    if (dpStartDate.getValue() == null || dpEndDate.getValue() == null) {
      showError("Vui lòng chọn ngày bắt đầu và ngày kết thúc");
      return;
    }

    if (cbStartHour.getValue() == null || cbStartMinute.getValue() == null ||
        cbEndHour.getValue() == null || cbEndMinute.getValue() == null) {
      showError("Vui lòng chọn giờ và phút cho thời gian");
      return;
    }

    LocalDateTime startTime = buildDateTime(
        dpStartDate.getValue(),
        cbStartHour.getValue(),
        cbStartMinute.getValue()
    );

    LocalDateTime endTime = buildDateTime(
        dpEndDate.getValue(),
        cbEndHour.getValue(),
        cbEndMinute.getValue()
    );

    if (!endTime.isAfter(startTime)) {
      showError("Thời gian kết thúc phải sau thời gian bắt đầu");
      return;
    }

    if (startTime.isBefore(LocalDateTime.now())) {
      showError("Thời gian bắt đầu phải >= thời gian hiện tại");
      return;
    }

    // IMAGE REQUIRED
    if (imageFiles.isEmpty()) {
      showError("Vui lòng chọn ít nhất 1 ảnh sản phẩm");
      return;
    }

    // CATEGORY REQUIRED FIELDS
    String category = cbCategory.getValue();
    CreateAuctionRequest req = new CreateAuctionRequest();

    req.setItemName(
            txtName.getText().trim()
    );

    req.setItemDescription(
            txtDescription.getText().trim()
    );

    req.setItemCategory(category);

    req.setStartingPrice(
            BigDecimal.valueOf(startPrice)
    );

    req.setStartTime(startTime);

    req.setEndTime(endTime);

    try {
      if (!imageFiles.isEmpty()) {
        File img = imageFiles.getFirst(); // lấy ảnh đầu tiên
        byte[] bytes = java.nio.file.Files.readAllBytes(img.toPath());
        String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
        req.setImageBase64(base64);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    CreateAuctionResponse response =
            SocketClient.getInstance()
                    .createAuction(req);

    // ======================
    // SUCCESS
    // ======================
    System.out.println("===== CREATE AUCTION SUCCESS =====");
    System.out.println("Name: " + txtName.getText());
    System.out.println("Category: " + category);
    System.out.println("Description: " + txtDescription.getText());
    System.out.println("Start Price: " + startPrice);
    System.out.println("Start Time: " + startTime);
    System.out.println("End Time: " + endTime);
    System.out.println("Images selected: " + imageFiles.size());
    System.out.println("==================================");
    // ======================
    // POPUP MESSAGE
    // ======================
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("Tạo phiên đấu giá");
    alert.setHeaderText("Phiên đấu giá đã được gửi đi");
    alert.setContentText("Thông tin phiên đấu giá sẽ được gửi đến admin.\nVui lòng chờ để được phê duyệt.");
    alert.showAndWait();
  }

  // ======================
  // HELPER METHODS
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

    if (category == null) {
      return;
    }

    System.out.println(
            "Đã chọn category: " + category
    );
  }
}