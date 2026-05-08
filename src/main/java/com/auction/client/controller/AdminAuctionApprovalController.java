package com.auction.client.controller;

import com.auction.common.dto.AdminAuctionRequestDTO;
import com.auction.common.request.ApproveAuctionRequest;
import com.auction.common.request.RejectAuctionRequest;
import com.auction.common.request.GetPendingAuctionRequestsRequest;
import com.auction.common.response.GetPendingAuctionRequestsResponse;

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
        try {
            loadPendingRequests();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

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

    private void loadPendingRequests() throws SQLException {

        ObservableList<AdminAuctionRequestDTO> list =
                FXCollections.observableArrayList();

        // lấy auction pending thật từ DB
        List<Auction> pendingAuctions = auctionService.getPendingAuctions();

        for (Auction auction : pendingAuctions) {

            Item item =
                    itemDAO.findById(
                            auction.getItemId()
                    );

            User seller =
                    userDAO.findById(
                            auction.getSellerId()
                    );

            if (item == null || seller == null) {
                continue;
            }

            AdminAuctionRequestDTO dto =
                    new AdminAuctionRequestDTO();

            dto.setRequestId(
                    auction.getId()
            );

            dto.setItemName(
                    item.getName()
            );

            dto.setSellerUsername(
                    seller.getUsername()
            );

            dto.setItemCategory(
                    item.getCategory().name()
            );

            dto.setItemDescription(
                    item.getDescription()
            );

            dto.setApprovalStatus(
                    auction.getStatus().name()
            );

            dto.setStartTime(
                    auction.getStartTime()
            );

            dto.setEndTime(
                    auction.getEndTime()
            );

            dto.setCreatedAt(
                    auction.getCreatedAt()
            );

            dto.setStartingPrice(
                    auction.getStartingPrice()
            );

            // nếu sau này có image_url
            dto.setImageUrl(
                    "https://picsum.photos/300"
            );

            list.add(dto);
        }

        tblAuctionRequests.setItems(list);
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
    private void handleApprove() throws SQLException {

        AdminAuctionRequestDTO selected =
                tblAuctionRequests.getSelectionModel()
                        .getSelectedItem();

        if (selected == null) {

            showAlert(
                    Alert.AlertType.WARNING,
                    "Warning",
                    "Vui lòng chọn yêu cầu."
            );

            return;
        }

        ApproveAuctionRequest request =
                new ApproveAuctionRequest();

        request.setRequestId(
                selected.getRequestId()
        );

        request.setAdminId(1);

        /*
         TODO:
         send request lên server
         */

        showAlert(
                Alert.AlertType.INFORMATION,
                "Success",
                "Đã duyệt thành công."
        );

        loadPendingRequests();
    }

    // =====================================================
    // REJECT
    // =====================================================

    @FXML
    private void handleReject() throws SQLException {

        AdminAuctionRequestDTO selected =
                tblAuctionRequests.getSelectionModel()
                        .getSelectedItem();

        if (selected == null) {

            showAlert(
                    Alert.AlertType.WARNING,
                    "Warning",
                    "Vui lòng chọn yêu cầu."
            );

            return;
        }

        String reason =
                txtRejectReason.getText();

        if (reason == null || reason.isBlank()) {

            showAlert(
                    Alert.AlertType.WARNING,
                    "Warning",
                    "Vui lòng nhập lý do."
            );

            return;
        }

        RejectAuctionRequest request =
                new RejectAuctionRequest();

        request.setRequestId(
                selected.getRequestId()
        );

        request.setAdminId(1);

        request.setRejectReason(reason);

        /*
         TODO:
         send request lên server
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
    private void handleRefresh() throws SQLException {

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