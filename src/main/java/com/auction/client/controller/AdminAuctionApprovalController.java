package com.auction.client.controller;

import com.auction.client.network.SocketClient;
import com.auction.client.session.ClientSession;
import com.auction.common.dto.AdminAuctionRequestDTO;
import com.auction.common.request.ApproveAuctionRequest;
import com.auction.common.request.RejectAuctionRequest;
import com.auction.common.request.GetPendingAuctionRequestsRequest;
import com.auction.common.response.ApproveAuctionResponse;
import com.auction.common.response.GetPendingAuctionRequestsResponse;

import com.auction.common.response.RejectAuctionResponse;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.net.URL;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

public class AdminAuctionApprovalController implements Initializable {

    // =====================================================
    // FORMATTER
    // =====================================================

    private final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // =====================================================
    // SEARCH + FILTER
    // =====================================================

    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cbStatusFilter;

    // =====================================================
    // TABLE
    // =====================================================

    @FXML private TableView<AdminAuctionRequestDTO> tblAuctionRequests;
    @FXML private TableColumn<AdminAuctionRequestDTO, Integer> colRequestId;
    @FXML private TableColumn<AdminAuctionRequestDTO, String> colProductName;
    @FXML private TableColumn<AdminAuctionRequestDTO, String> colSeller;
    @FXML private TableColumn<AdminAuctionRequestDTO, String> colStartTime;
    @FXML private TableColumn<AdminAuctionRequestDTO, String> colEndTime;

    // =====================================================
    // DETAIL PANEL
    // =====================================================

    @FXML private ImageView imgProduct;
    @FXML private Label lblProductName;
    @FXML private Label lblSeller;
    @FXML private Label lblCategory;
    @FXML private Label lblStartPrice;
    @FXML private Label lblStartTime;
    @FXML private Label lblEndTime;
    @FXML private Label lblCreatedAt;
    @FXML private TextArea txtDescription;
    @FXML private TextArea txtRejectReason;
    @FXML private Label lblStepPrice;

    // =====================================================
    // BUTTONS
    // =====================================================

    @FXML private Button btnApprove;
    @FXML private Button btnReject;
    @FXML private Button btnRefresh;

    // =====================================================
    // INITIALIZE
    // =====================================================
    @Override
    public void initialize(URL location, ResourceBundle resources) {

        setupTableColumns();
        initializeStatusFilter();
        setupTableSelection();
        loadPendingRequests();

        tblAuctionRequests.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        );
    }

    // =====================================================
    // TABLE COLUMNS
    // =====================================================

    private void setupTableColumns() {

        colRequestId.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getRequestId()).asObject());

        colProductName.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getItemName()));

        colSeller.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getSellerUsername()));

        colStartTime.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue()
                                .getStartTime()
                                .format(formatter)
                ));

        colEndTime.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue()
                                .getEndTime()
                                .format(formatter)
                ));
    }

    // =====================================================
    // STATUS FILTER
    // =====================================================

    private void initializeStatusFilter() {

        cbStatusFilter.setItems(
                FXCollections.observableArrayList(
                        "ALL",
                        "PENDING",
                        "APPROVED",
                        "REJECTED"
                )
        );

        cbStatusFilter.setValue("PENDING");
    }

    // =====================================================
    // LOAD REQUESTS
    // =====================================================

    private void loadPendingRequests() {
        new Thread(() -> {
            GetPendingAuctionRequestsRequest request = new GetPendingAuctionRequestsRequest();
            GetPendingAuctionRequestsResponse response =
                    SocketClient.getInstance().getPendingAuctionRequests(request);

            // THÊM DÒNG NÀY
            System.out.println("=== CLIENT DEBUG: response = " +
                    (response != null ? response.isSuccess() + " | " + response.getMessage() : "null"));

            javafx.application.Platform.runLater(() -> {
                if (response == null || response.getRequests() == null) {
                    tblAuctionRequests.setItems(FXCollections.observableArrayList());
                    return;
                }
                ObservableList<AdminAuctionRequestDTO> list =
                        FXCollections.observableArrayList(response.getRequests());
                tblAuctionRequests.setItems(list);
            });
        }, "load-pending-thread").start();
    }

    // =====================================================
    // TABLE SELECTION
    // =====================================================

    private void setupTableSelection() {

        tblAuctionRequests.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldValue, selectedItem) -> {

                    if (selectedItem != null) {

                        showRequestDetails(selectedItem);

                    }

                });

    }

    // =====================================================
    // SHOW DETAILS
    // =====================================================

    private void showRequestDetails(
            AdminAuctionRequestDTO request
    ) {

        lblProductName.setText(
                request.getItemName()
        );

        lblSeller.setText(
                request.getSellerUsername()
        );

        lblCategory.setText(
                request.getItemCategory()
        );

        lblStartPrice.setText(
                request.getStartingPrice() + " VNĐ"
        );

        lblStepPrice.setText(
            request.getStepPrice() != null
                ? request.getStepPrice().toPlainString() + " VNĐ"
                : "---"
        );

        lblStartTime.setText(
                request.getStartTime().format(formatter)
        );

        lblEndTime.setText(
                request.getEndTime().format(formatter)
        );

        lblCreatedAt.setText(
                request.getCreatedAt().format(formatter)
        );

        txtDescription.setText(
                request.getItemDescription()
        );

        try {

            imgProduct.setImage(
                    new Image(request.getImageUrl())
            );

        } catch (Exception e) {

            System.out.println("Cannot load image.");

        }
    }

    // =====================================================
    // APPROVE
    // =====================================================

    @FXML
    private void handleApprove() {
        AdminAuctionRequestDTO selected =
                tblAuctionRequests.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Warning", "Vui lòng chọn yêu cầu.");
            return;
        }

        ApproveAuctionRequest request = new ApproveAuctionRequest();
        request.setRequestId(selected.getRequestId());
        request.setAdminId(ClientSession.getCurrentUser().getId());

        btnApprove.setDisable(true);
        btnReject.setDisable(true);
        btnRefresh.setDisable(true);

        new Thread(() -> {
            ApproveAuctionResponse response =
                    SocketClient.getInstance().approveAuction(request);

            javafx.application.Platform.runLater(() -> {
                btnApprove.setDisable(false);
                btnReject.setDisable(false);
                btnRefresh.setDisable(false);

                if (response.isSuccess()) {
                    showAlert(Alert.AlertType.INFORMATION, "Success", response.getMessage());
                    tblAuctionRequests.getItems().remove(selected);
                    tblAuctionRequests.getSelectionModel().clearSelection();
                    // Đợi 1500ms rồi mới load lại
                    javafx.animation.PauseTransition pause =
                            new javafx.animation.PauseTransition(
                                    javafx.util.Duration.millis(1500));
                    pause.setOnFinished(ev -> loadPendingRequests());
                    pause.play();
                } else {
                    showAlert(Alert.AlertType.ERROR, "Error", response.getMessage());
                }
            });
        }, "approve-thread").start();
    }

    @FXML
    private void handleReject() {
        AdminAuctionRequestDTO selected =
                tblAuctionRequests.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Warning", "Vui lòng chọn yêu cầu.");
            return;
        }

        String reason = txtRejectReason.getText();
        if (reason == null || reason.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Warning", "Vui lòng nhập lý do.");
            return;
        }

        RejectAuctionRequest request = new RejectAuctionRequest();
        request.setRequestId(selected.getRequestId());
        request.setAdminId(ClientSession.getCurrentUser().getId());
        request.setRejectReason(reason);

        btnApprove.setDisable(true);
        btnReject.setDisable(true);
        btnRefresh.setDisable(true);

        new Thread(() -> {
            RejectAuctionResponse response =
                    SocketClient.getInstance().rejectAuction(request);

            javafx.application.Platform.runLater(() -> {
                btnApprove.setDisable(false);
                btnReject.setDisable(false);
                btnRefresh.setDisable(false);

                if (response == null) {
                    showAlert(Alert.AlertType.ERROR, "Error", "Server error");
                    return;
                }
                if (response.isSuccess()) {
                    showAlert(Alert.AlertType.INFORMATION, "Success", response.getMessage());
                    tblAuctionRequests.getItems().remove(selected);
                    tblAuctionRequests.getSelectionModel().clearSelection();
                    //  Đợi 500ms rồi mới load lại
                    javafx.animation.PauseTransition pause =
                            new javafx.animation.PauseTransition(
                                    javafx.util.Duration.millis(1500));
                    pause.setOnFinished(ev -> loadPendingRequests());
                    pause.play();
                } else {
                    showAlert(Alert.AlertType.ERROR, "Error", response.getMessage());
                }
            });
        }, "reject-thread").start();
    }

    // =====================================================
    // REFRESH
    // =====================================================

    @FXML
    void handleRefresh(){
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