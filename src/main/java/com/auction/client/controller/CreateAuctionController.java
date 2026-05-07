package com.auction.client.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
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
  // ART FIELDS
  // ======================
  @FXML private VBox artFields;
  @FXML private TextField txtArtist;
  @FXML private TextField txtArtYear;
  @FXML private TextField txtMaterial;

  // ======================
  // ELECTRONICS FIELDS
  // ======================
  @FXML private VBox electronicFields;
  @FXML private TextField txtBrand;
  @FXML private TextField txtModel;
  @FXML private TextField txtWarranty;

  // ======================
  // VEHICLES FIELDS
  // ======================
  @FXML private VBox vehicleFields;
  @FXML private TextField txtCarBrand;
  @FXML private TextField txtCarModel;
  @FXML private TextField txtYear;
  @FXML private TextField txtMileage;

  // ======================
  // IMAGES
  // ======================
  @FXML private ListView<File> lvImages;
  @FXML private ImageView imgPreview;

  private final ObservableList<File> imageFiles = FXCollections.observableArrayList();

  // ======================
  // INIT
  // ======================
  @FXML
  public void initialize() {

    // Category options
    cbCategory.getItems().addAll("Art", "Electronics", "Vehicles");

    // Hide dynamic fields
    hideAllDynamicFields();

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
  @FXML
  public void handleCategoryChange() {

    String category = cbCategory.getValue();
    hideAllDynamicFields();

    if (category == null) return;

    switch (category) {
      case "Art" -> showFields(artFields);
      case "Electronics" -> showFields(electronicFields);
      case "Vehicles" -> showFields(vehicleFields);
    }
  }

  private void hideAllDynamicFields() {
    hideFields(artFields);
    hideFields(electronicFields);
    hideFields(vehicleFields);
  }

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

    if ("Art".equals(category)) {
      if (isEmpty(txtArtist) || isEmpty(txtArtYear) || isEmpty(txtMaterial)) {
        showError("Danh mục Art yêu cầu nhập Tác giả, Năm ra đời và Chất liệu");
        return;
      }
    }

    if ("Electronics".equals(category)) {
      if (isEmpty(txtBrand) || isEmpty(txtModel) || isEmpty(txtWarranty)) {
        showError("Danh mục Electronics yêu cầu nhập Brand, Model và Warranty");
        return;
      }

      try {
        int warranty = Integer.parseInt(txtWarranty.getText().trim());
        if (warranty < 0) {
          showError("Warranty phải >= 0");
          return;
        }
      } catch (NumberFormatException e) {
        showError("Warranty phải là số nguyên");
        return;
      }
    }

    if ("Vehicles".equals(category)) {
      if (isEmpty(txtCarBrand) || isEmpty(txtCarModel) || isEmpty(txtYear) || isEmpty(txtMileage)) {
        showError("Danh mục Vehicles yêu cầu nhập Hãng xe, Dòng xe, Năm và Số km đã đi");
        return;
      }

      try {
        int year = Integer.parseInt(txtYear.getText().trim());
        if (year < 1900 || year > LocalDate.now().getYear() + 1) {
          showError("Năm sản xuất không hợp lệ");
          return;
        }
      } catch (NumberFormatException e) {
        showError("Năm sản xuất phải là số");
        return;
      }

      try {
        double km = Double.parseDouble(txtMileage.getText().trim());
        if (km < 0) {
          showError("Số km đã đi phải >= 0");
          return;
        }
      } catch (NumberFormatException e) {
        showError("Số km đã đi phải là số");
        return;
      }
    }

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

    // TODO: Send to server later
    // SocketClient.getInstance().createAuction(...)

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
}