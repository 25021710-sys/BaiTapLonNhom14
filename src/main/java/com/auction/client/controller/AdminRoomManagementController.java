package com.auction.client.controller;

import com.auction.client.network.SocketClient;
import com.auction.common.dto.AdminRoomDTO;
import com.auction.common.dto.AuctionUpdateDTO;
import com.auction.common.response.AdminGetRoomsResponse;
import com.auction.common.response.AdminRoomDetailResponse;
import com.auction.common.response.SimpleResponse;
import com.auction.server.model.AuctionStatus;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.net.URL;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * AdminRoomManagementController – Màn hình giám sát phòng đấu giá realtime.
 *
 * Luồng hoạt động:
 *   1. initialize() → loadRooms() lần đầu + đăng ký pushCallback cho realtime update
 *   2. Người dùng chọn hàng → loadRoomDetail() → hiển thị detail panel
 *   3. searchField thay đổi → lọc FilteredList tại client (không cần gọi server lại)
 *   4. Các nút Pause/Resume/Close → gọi server → reload danh sách
 *   5. pushCallback nhận AuctionUpdateDTO từ server → cập nhật log + detail panel tự động
 */
public class AdminRoomManagementController implements Initializable {

    // ===== UI: Top Bar =====
    @FXML private TextField searchField;

    // ===== UI: Table =====
    @FXML private TableView<AdminRoomDTO> roomTable;
    @FXML private TableColumn<AdminRoomDTO, String>  colRoomId;
    @FXML private TableColumn<AdminRoomDTO, String>  colItem;
    @FXML private TableColumn<AdminRoomDTO, String>  colPrice;
    @FXML private TableColumn<AdminRoomDTO, Integer> colBids;
    @FXML private TableColumn<AdminRoomDTO, String>  colTime;
    @FXML private TableColumn<AdminRoomDTO, String>  colStatus;

    // ===== UI: Detail Panel =====
    @FXML private Label lblRoomId;
    @FXML private Label lblItem;
    @FXML private Label lblStartPrice;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblHighestBidder;
    @FXML private Label lblTimeLeft;

    @FXML private TextArea  logArea;
    @FXML private ListView<String> participantList;

    // ===== UI: Bottom =====
    @FXML private Label lblSystemStatus;

    // ===== State =====
    private final ObservableList<AdminRoomDTO> allRooms   = FXCollections.observableArrayList();
    private FilteredList<AdminRoomDTO>         filteredRooms;
    private AdminRoomDTO                       selectedRoom;
    private javafx.animation.Timeline clockTimeline;

    private static final NumberFormat CURRENCY_FMT =
            NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    // ======================================================================
    // INITIALIZE
    // ======================================================================

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTableColumns();
        setupTableSelection();
        setupSearch();
        registerPushCallback();
        loadRooms();
        startClock();
    }

    // ======================================================================
    // SETUP
    // ======================================================================

    /** Bind từng cột vào field của AdminRoomDTO. */
    private void setupTableColumns() {
        colRoomId.setCellValueFactory(c ->
                new SimpleStringProperty("#" + c.getValue().getAuctionId()));

        colItem.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getItemName() != null
                        ? c.getValue().getItemName() : "—"));

        colPrice.setCellValueFactory(c -> {
            AdminRoomDTO r = c.getValue();
            String price = r.getCurrentPrice() != null
                    ? CURRENCY_FMT.format(r.getCurrentPrice()) + " đ"
                    : "—";
            return new SimpleStringProperty(price);
        });

        colBids.setCellValueFactory(c ->
                new SimpleIntegerProperty(c.getValue().getTotalBids()).asObject());

        colTime.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getTimeLeftFormatted()));

        colStatus.setCellValueFactory(c -> {
            AuctionStatus status = c.getValue().getStatus();
            return new SimpleStringProperty(status != null ? status.getDisplay() : "—");
        });
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.contains("Đang")) {
                        setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    } else if (item.contains("Sắp")) {
                        setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #7f8c8d;");
                    }
                }
            }
        });

        // Gắn FilteredList vào table
        filteredRooms = new FilteredList<>(allRooms, r -> true);
        roomTable.setItems(filteredRooms);
    }

    /** Khi chọn hàng → load detail phòng từ server. */
    private void setupTableSelection() {
        roomTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        selectedRoom = newVal;
                        loadRoomDetail(newVal.getAuctionId());
                    }
                });
    }

    /** Lọc bảng theo searchField (client-side, không gọi server). */
    private void setupSearch() {
        searchField.textProperty().addListener((obs, oldVal, keyword) -> {
            String kw = keyword == null ? "" : keyword.trim().toLowerCase();
            filteredRooms.setPredicate(room -> {
                if (kw.isEmpty()) return true;
                if (String.valueOf(room.getAuctionId()).contains(kw)) return true;
                if (room.getItemName() != null && room.getItemName().toLowerCase().contains(kw)) return true;
                if (room.getSellerName() != null && room.getSellerName().toLowerCase().contains(kw)) return true;
                return false;
            });
        });
    }

    /**
     * Đăng ký callback nhận push update realtime từ server.
     * Khi có bid mới hoặc trạng thái thay đổi, cập nhật log + detail panel.
     */
    private void registerPushCallback() {
        SocketClient.getInstance().setPushCallback(update -> {
            // Đã trên FX thread (Platform.runLater đã được gọi trong SocketClient)
            handlePushUpdate(update);
        });
    }

    // ======================================================================
    // LOAD DATA
    // ======================================================================

    /** Load toàn bộ danh sách phòng từ server (chạy background thread). */
    private void loadRooms() {
        lblSystemStatus.setText("Đang tải...");
        lblSystemStatus.setStyle("-fx-text-fill: #f39c12;");

        Task<AdminGetRoomsResponse> task = new Task<>() {
            @Override
            protected AdminGetRoomsResponse call() {
                return SocketClient.getInstance().adminGetRooms(null, null);
            }
        };

        task.setOnSucceeded(e -> {
            AdminGetRoomsResponse resp = task.getValue();
            if (resp.isSuccess() && resp.getRooms() != null) {
                allRooms.setAll(resp.getRooms());
                lblSystemStatus.setText("ONLINE — " + resp.getRooms().size() + " phòng đang hoạt động");
                lblSystemStatus.setStyle("-fx-text-fill: #27ae60;");
                appendLog("[SYSTEM] Danh sách phòng đã cập nhật (" + resp.getRooms().size() + " phòng)\n");
            } else {
                lblSystemStatus.setText("LỖI: " + resp.getMessage());
                lblSystemStatus.setStyle("-fx-text-fill: #e74c3c;");
                appendLog("[LỖI] Không lấy được danh sách phòng: " + resp.getMessage() + "\n");
            }
        });

        task.setOnFailed(e -> {
            lblSystemStatus.setText("LỖI KẾT NỐI");
            lblSystemStatus.setStyle("-fx-text-fill: #e74c3c;");
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    /** Load chi tiết 1 phòng (participants + bid log). */
    private void loadRoomDetail(int auctionId) {
        Task<AdminRoomDetailResponse> task = new Task<>() {
            @Override
            protected AdminRoomDetailResponse call() {
                return SocketClient.getInstance().adminGetRoomDetail(auctionId);
            }
        };

        task.setOnSucceeded(e -> {
            AdminRoomDetailResponse resp = task.getValue();
            if (resp.isSuccess() && resp.getRoom() != null) {
                showRoomDetail(resp.getRoom());
            } else {
                appendLog("[LỖI] Không lấy được chi tiết phòng: " + resp.getMessage() + "\n");
            }
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ======================================================================
    // SHOW DETAIL PANEL
    // ======================================================================

    /** Điền thông tin chi tiết phòng vào detail panel bên phải. */
    private void showRoomDetail(AdminRoomDTO room) {
        lblRoomId.setText("Room: #" + room.getAuctionId());
        lblItem.setText("Item: " + (room.getItemName() != null ? room.getItemName() : "—"));
        lblStartPrice.setText("Giá khởi điểm: "
                + (room.getStartingPrice() != null
                ? CURRENCY_FMT.format(room.getStartingPrice()) + " đ" : "—"));
        lblCurrentPrice.setText("Giá hiện tại: "
                + (room.getCurrentPrice() != null
                ? CURRENCY_FMT.format(room.getCurrentPrice()) + " đ" : "—"));
        lblHighestBidder.setText("Người dẫn đầu: "
                + (room.getHighestBidderUsername() != null
                ? room.getHighestBidderUsername() : "Chưa có"));
        lblTimeLeft.setText("Còn lại: " + room.getTimeLeftFormatted());

        // Participants
        if (room.getParticipantUsernames() != null) {
            participantList.setItems(FXCollections.observableArrayList(room.getParticipantUsernames()));
        } else {
            participantList.setItems(FXCollections.emptyObservableList());
        }

        // Bid log
        if (room.getRecentBidLogs() != null) {
            logArea.clear();
            room.getRecentBidLogs().forEach(log -> logArea.appendText(log + "\n"));
        }
    }

    // ======================================================================
    // PUSH UPDATE HANDLER (realtime từ server)
    // ======================================================================

    /**
     * Xử lý AuctionUpdateDTO đẩy từ server.
     * Cập nhật live log và làm mới detail panel nếu phòng đang được chọn.
     */
    private void handlePushUpdate(AuctionUpdateDTO update) {
        // Cập nhật log
        String logLine = switch (update.getType()) {
            case BID_PLACED -> String.format("[BID] Room #%d — %s đặt %s đ\n",
                    update.getAuctionId(),
                    update.getHighestBidderUsername(),
                    CURRENCY_FMT.format(update.getNewPrice()));
            case AUCTION_ENDED -> String.format("[KẾT THÚC] Room #%d — Người thắng: %s\n",
                    update.getAuctionId(),
                    update.getHighestBidderUsername() != null
                            ? update.getHighestBidderUsername() : "Không có");
            case AUCTION_EXTENDED -> String.format("[GIA HẠN] Room #%d — %s\n",
                    update.getAuctionId(), update.getMessage());
            case PARTICIPANT_CHANGED -> String.format("[THAM GIA] Room #%d — %s\n",
                    update.getAuctionId(), update.getMessage());
            default -> String.format("[%s] Room #%d — %s\n",
                    update.getType(), update.getAuctionId(), update.getMessage());
        };
        appendLog(logLine);

        // Cập nhật dòng tương ứng trong bảng
        allRooms.stream()
                .filter(r -> r.getAuctionId() == update.getAuctionId())
                .findFirst()
                .ifPresent(r -> {
                    if (update.getNewPrice() != null) r.setCurrentPrice(update.getNewPrice());
                    if (update.getHighestBidderUsername() != null)
                        r.setHighestBidderUsername(update.getHighestBidderUsername());
                    r.setTotalBids(r.getTotalBids() + 1);
                    // Force refresh bảng
                    roomTable.refresh();
                });

        // Nếu đang xem phòng này thì reload detail
        if (selectedRoom != null && selectedRoom.getAuctionId() == update.getAuctionId()) {
            loadRoomDetail(update.getAuctionId());
        }
    }

    // ======================================================================
    // ACTIONS
    // ======================================================================

    @FXML
    public void handleRefresh(ActionEvent event) {
        searchField.clear();
        loadRooms();
        appendLog("[SYSTEM] Refresh danh sách phòng\n");
    }

    /** Tạm dừng phòng đang được chọn. */
    @FXML
    public void handlePauseRoom(ActionEvent event) {
        if (selectedRoom == null) {
            showAlert(Alert.AlertType.WARNING, "Chưa chọn phòng", "Vui lòng chọn một phòng từ bảng.");
            return;
        }
        if (selectedRoom.getStatus() != AuctionStatus.RUNNING) {
            showAlert(Alert.AlertType.WARNING, "Không thể tạm dừng",
                    "Chỉ có thể tạm dừng phòng đang RUNNING.\nTrạng thái hiện tại: "
                            + selectedRoom.getStatus().getDisplay());
            return;
        }

        String reason = showInputDialog("Lý do tạm dừng", "Nhập lý do tạm dừng phòng #" + selectedRoom.getAuctionId() + ":");
        if (reason == null) return; // Người dùng bấm Cancel

        runAsync("ADMIN_PAUSE_ROOM", () ->
                        SocketClient.getInstance().adminPauseRoom(selectedRoom.getAuctionId(), reason),
                resp -> {
                    appendLog("[ADMIN] " + (resp.isSuccess() ? "Đã tạm dừng" : "Lỗi: " + resp.getMessage())
                            + " phòng #" + selectedRoom.getAuctionId() + "\n");
                    if (resp.isSuccess()) loadRooms();
                }
        );
    }

    /** Tiếp tục phòng đang tạm dừng. */
    @FXML
    public void handleResumeRoom(ActionEvent event) {
        if (selectedRoom == null) {
            showAlert(Alert.AlertType.WARNING, "Chưa chọn phòng", "Vui lòng chọn một phòng từ bảng.");
            return;
        }
        if (selectedRoom.getStatus() != AuctionStatus.OPEN) {
            showAlert(Alert.AlertType.WARNING, "Không thể tiếp tục",
                    "Chỉ có thể tiếp tục phòng đang TẠM DỪNG (OPEN).\nTrạng thái hiện tại: "
                            + selectedRoom.getStatus().getDisplay());
            return;
        }

        runAsync("ADMIN_RESUME_ROOM", () ->
                        SocketClient.getInstance().adminResumeRoom(selectedRoom.getAuctionId()),
                resp -> {
                    appendLog("[ADMIN] " + (resp.isSuccess() ? "Đã tiếp tục" : "Lỗi: " + resp.getMessage())
                            + " phòng #" + selectedRoom.getAuctionId() + "\n");
                    if (resp.isSuccess()) loadRooms();
                }
        );
    }

    /** Đóng cưỡng bức phòng. */
    @FXML
    public void handleCloseRoom(ActionEvent event) {
        if (selectedRoom == null) {
            showAlert(Alert.AlertType.WARNING, "Chưa chọn phòng", "Vui lòng chọn một phòng từ bảng.");
            return;
        }

        // Xác nhận trước khi đóng
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận đóng phòng");
        confirm.setHeaderText("Đóng cưỡng bức phòng #" + selectedRoom.getAuctionId() + "?");
        confirm.setContentText("Hành động này không thể hoàn tác. Tất cả người dùng đang tham gia sẽ bị ngắt.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        String reason = showInputDialog("Lý do đóng phòng", "Nhập lý do đóng phòng #" + selectedRoom.getAuctionId() + ":");
        if (reason == null) return;

        runAsync("ADMIN_CLOSE_ROOM", () ->
                        SocketClient.getInstance().adminCloseRoom(selectedRoom.getAuctionId(), reason),
                resp -> {
                    appendLog("[ADMIN] " + (resp.isSuccess() ? "Đã đóng phòng" : "Lỗi: " + resp.getMessage())
                            + " #" + selectedRoom.getAuctionId() + "\n");
                    if (resp.isSuccess()) {
                        selectedRoom = null;
                        clearDetailPanel();
                        loadRooms();
                    }
                }
        );
    }

    // ======================================================================
    // HELPERS
    // ======================================================================

    private void appendLog(String text) {
        logArea.appendText(text);
        // Tự cuộn xuống cuối
        logArea.setScrollTop(Double.MAX_VALUE);
    }

    private void clearDetailPanel() {
        lblRoomId.setText("Room: -");
        lblItem.setText("Item: -");
        lblStartPrice.setText("Start Price: -");
        lblCurrentPrice.setText("Current: -");
        lblHighestBidder.setText("Highest Bidder: -");
        lblTimeLeft.setText("Time Left: -");
        logArea.clear();
        participantList.setItems(FXCollections.emptyObservableList());
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private String showInputDialog(String title, String prompt) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(prompt);
        return dialog.showAndWait().orElse(null);
    }

    /**
     * Chạy request lên server trên background thread, callback lên FX thread.
     *
     * @param taskName tên hiển thị trong log nếu lỗi
     * @param supplier lambda gọi SocketClient
     * @param onSuccess lambda nhận SimpleResponse để xử lý kết quả
     */
    private void runAsync(String taskName,
                          java.util.concurrent.Callable<SimpleResponse> supplier,
                          java.util.function.Consumer<SimpleResponse> onSuccess) {
        Task<SimpleResponse> task = new Task<>() {
            @Override
            protected SimpleResponse call() throws Exception {
                return supplier.call();
            }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> {
            appendLog("[LỖI] " + taskName + ": " + task.getException().getMessage() + "\n");
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }
    /** Đồng hồ đếm ngược: cập nhật cột Time Left và lblTimeLeft mỗi giây. */
    private void startClock() {
        clockTimeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(
                        javafx.util.Duration.seconds(1),
                        e -> {
                            roomTable.refresh(); // cập nhật cột Time Left trong bảng
                            if (selectedRoom != null) {
                                lblTimeLeft.setText("Còn lại: " + selectedRoom.getTimeLeftFormatted());
                            }
                        }
                )
        );
        clockTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        clockTimeline.play();
    }

    /** Gọi khi view bị unload để dừng timer, tránh memory leak. */
    public void cleanup() {
        if (clockTimeline != null) clockTimeline.stop();
    }
}