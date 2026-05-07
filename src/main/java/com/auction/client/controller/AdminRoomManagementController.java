package com.auction.client.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ResourceBundle;

public class AdminRoomManagementController implements Initializable {

    // ===== UI =====
    @FXML private TableView<?> roomTable;

    @FXML private TextField searchField;
    @FXML private Label lblSystemStatus;

    // Detail panel
    @FXML private Label lblRoomId;
    @FXML private Label lblItem;
    @FXML private Label lblStartPrice;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblHighestBidder;
    @FXML private Label lblTimeLeft;

    @FXML private TextArea logArea;
    @FXML private ListView<?> participantList;

    // ===== STATE =====
    private Object selectedRoom; // sau này thay bằng AuctionRoomDTO

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        System.out.println("Admin Room Monitoring initialized");

        setupTableSelection();

        loadRooms();
    }

    // =========================
    // LOAD ROOMS (replace FlowPane)
    // =========================
    private void loadRooms() {
        System.out.println("Loading rooms from server...");

        // TODO: gọi API / Socket
        lblSystemStatus.setText("System ONLINE - Rooms loaded");
    }

    // =========================
    // TABLE SELECTION
    // =========================
    private void setupTableSelection() {
        roomTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {

                    if (newVal != null) {
                        selectedRoom = newVal;
                        showRoomDetail(newVal);
                    }
                });
    }

    // =========================
    // SHOW DETAIL PANEL
    // =========================
    private void showRoomDetail(Object room) {

        System.out.println("Inspect room: " + room);

        // TODO map DTO thật
        lblRoomId.setText("Room: A12");
        lblItem.setText("iPhone 15");
        lblStartPrice.setText("$500");
        lblCurrentPrice.setText("$900");
        lblHighestBidder.setText("user123");
        lblTimeLeft.setText("02:15");

        logArea.appendText("[SYSTEM] Room selected\n");
    }

    // =========================
    // ADMIN ACTIONS (NO CREATE)
    // =========================

    @FXML
    public void handleRefresh(ActionEvent event) {
        System.out.println("Refreshing room list...");
        loadRooms();
    }

    // PAUSE ROOM
    @FXML
    public void handlePauseRoom(ActionEvent event) {
        if (selectedRoom == null) return;

        System.out.println("PAUSE room");

        // TODO call server: pauseAuction(roomId)
        logArea.appendText("[ADMIN] Room paused\n");
    }

    // RESUME ROOM
    @FXML
    public void handleResumeRoom(ActionEvent event) {
        if (selectedRoom == null) return;

        System.out.println("RESUME room");

        // TODO call server: resumeAuction(roomId)
        logArea.appendText("[ADMIN] Room resumed\n");
    }

    // CLOSE ROOM (force end)
    @FXML
    public void handleCloseRoom(ActionEvent event) {
        if (selectedRoom == null) return;

        System.out.println("CLOSE room");

        // TODO call server: closeAuction(roomId)
        logArea.appendText("[ADMIN] Room closed permanently\n");
    }
}