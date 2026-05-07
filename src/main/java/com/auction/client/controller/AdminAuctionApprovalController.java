package com.auction.client.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.net.URL;
import java.util.ResourceBundle;

public class AdminAuctionApprovalController implements Initializable {

    // =====================================================
    // SEARCH + FILTER
    // =====================================================

    @FXML
    private TextField txtSearch;

    @FXML
    private ComboBox<String> cbStatusFilter;

    // =====================================================
    // TABLE
    // =====================================================

    @FXML
    private TableView<Object> tblAuctionRequests;

    @FXML
    private TableColumn<Object, String> colRequestId;

    @FXML
    private TableColumn<Object, String> colProductName;

    @FXML
    private TableColumn<Object, String> colSeller;

    @FXML
    private TableColumn<Object, String> colCategory;

    @FXML
    private TableColumn<Object, String> colStartPrice;

    @FXML
    private TableColumn<Object, String> colStatus;

    // =====================================================
    // DETAIL PANEL
    // =====================================================

    @FXML
    private ImageView imgProduct;

    @FXML
    private Label lblProductName;

    @FXML
    private Label lblSeller;

    @FXML
    private Label lblCategory;

    @FXML
    private Label lblStartPrice;

    @FXML
    private Label lblStartTime;

    @FXML
    private Label lblEndTime;

    @FXML
    private Label lblCreatedAt;

    @FXML
    private TextArea txtDescription;

    @FXML
    private TextArea txtRejectReason;

    // =====================================================
    // BUTTONS
    // =====================================================

    @FXML
    private Button btnApprove;

    @FXML
    private Button btnReject;

    @FXML
    private Button btnRefresh;

    // =====================================================
    // INITIALIZE
    // =====================================================

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        initializeStatusFilter();

        loadPendingRequests();

        setupTableSelection();

    }

    // =====================================================
    // STATUS FILTER
    // =====================================================

    private void initializeStatusFilter() {

        ObservableList<String> statuses = FXCollections.observableArrayList(
                "ALL",
                "PENDING",
                "APPROVED",
                "REJECTED"
        );

        cbStatusFilter.setItems(statuses);

        cbStatusFilter.setValue("PENDING");
    }

    // =====================================================
    // LOAD REQUESTS
    // =====================================================

    private void loadPendingRequests() {

        /*
         TODO:
         Load dữ liệu từ server/database

         Ví dụ:
         List<AuctionRequest> requests =
             auctionService.getPendingRequests();

         tblAuctionRequests.setItems(...);
         */

    }

    // =====================================================
    // TABLE SELECTION
    // =====================================================

    private void setupTableSelection() {

        tblAuctionRequests.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldValue, newValue) -> {

                    if (newValue != null) {

                        showRequestDetails(newValue);

                    }

                });

    }

    // =====================================================
    // SHOW DETAILS
    // =====================================================

    private void showRequestDetails(Object request) {

        /*
         TODO:
         Cast request sang AuctionRequest

         Sau đó set dữ liệu:
         */

        lblProductName.setText("Laptop Gaming MSI");

        lblSeller.setText("Thịnh Văn Đức");

        lblCategory.setText("Electronics");

        lblStartPrice.setText("15.000.000 VNĐ");

        lblStartTime.setText("07/05/2026 18:00");

        lblEndTime.setText("08/05/2026 18:00");

        lblCreatedAt.setText("07/05/2026");

        txtDescription.setText("""
                Laptop MSI RTX 4070
                RAM 32GB
                SSD 1TB
                Tình trạng mới 95%
                """);

        try {

            Image image = new Image(
                    getClass().getResourceAsStream(
                            "/image/default-product.png"
                    )
            );

            imgProduct.setImage(image);

        } catch (Exception e) {

            System.out.println("Không load được ảnh.");

        }

    }

    // =====================================================
    // APPROVE
    // =====================================================

    @FXML
    private void handleApprove() {

        Object selectedRequest =
                tblAuctionRequests.getSelectionModel()
                        .getSelectedItem();

        if (selectedRequest == null) {

            showAlert(
                    Alert.AlertType.WARNING,
                    "Warning",
                    "Vui lòng chọn yêu cầu cần duyệt."
            );

            return;
        }

        /*
         TODO:

         1. Update status -> APPROVED

         2. Tạo Auction Room

         3. Broadcast update dashboard

         */

        showAlert(
                Alert.AlertType.INFORMATION,
                "Success",
                "Đã duyệt phiên đấu giá thành công."
        );

        loadPendingRequests();

    }

    // =====================================================
    // REJECT
    // =====================================================

    @FXML
    private void handleReject() {

        Object selectedRequest =
                tblAuctionRequests.getSelectionModel()
                        .getSelectedItem();

        if (selectedRequest == null) {

            showAlert(
                    Alert.AlertType.WARNING,
                    "Warning",
                    "Vui lòng chọn yêu cầu cần từ chối."
            );

            return;
        }

        String reason = txtRejectReason.getText();

        if (reason == null || reason.trim().isEmpty()) {

            showAlert(
                    Alert.AlertType.WARNING,
                    "Warning",
                    "Vui lòng nhập lý do từ chối."
            );

            return;
        }

        /*
         TODO:

         1. Update status -> REJECTED

         2. Save reject reason

         */

        showAlert(
                Alert.AlertType.INFORMATION,
                "Rejected",
                "Đã từ chối yêu cầu."
        );

        loadPendingRequests();

    }

    // =====================================================
    // REFRESH
    // =====================================================

    @FXML
    private void handleRefresh() {

        loadPendingRequests();

    }

    // =====================================================
    // ALERT
    // =====================================================

    private void showAlert(
            Alert.AlertType type,
            String title,
            String message
    ) {

        Alert alert = new Alert(type);

        alert.setTitle(title);

        alert.setHeaderText(null);

        alert.setContentText(message);

        alert.showAndWait();

    }

}
