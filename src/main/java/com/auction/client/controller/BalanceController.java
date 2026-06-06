package com.auction.client.controller;

import com.auction.client.network.SocketClient;
import com.auction.common.dto.DepositRecord;
import com.auction.common.dto.UserDTO;
import com.auction.common.request.BalanceRequest;
import com.auction.common.request.DepositHistoryRequest;
import com.auction.common.response.BalanceResponse;
import com.auction.client.session.ClientSession;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.text.TextFlow;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * BalanceController - xử lý giao diện nạp/rút tiền.
 * Giao tiếp với server qua SocketClient.
 */
public class BalanceController {

    @FXML private TextField depositField;
    @FXML private TextField withdrawField;
    @FXML private Button    confirmButton;
    @FXML private Button    cancelButton;
    @FXML private TextFlow  depositFlow;
    @FXML private TextFlow  withdrawFlow;
    @FXML private Label     balanceLabel;
    @FXML private Label     errorLabel;

    @FXML private TableView<DepositRecord>            historyTable;
    @FXML private TableColumn<DepositRecord, String>  colType;
    @FXML private TableColumn<DepositRecord, String>  colAmount;
    @FXML private TableColumn<DepositRecord, String>  colTime;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final NumberFormat VND_FMT =
            NumberFormat.getInstance(Locale.of("vi", "VN"));

    private boolean isDeposit = true;

    @FXML
    public void initialize() {
        if (ClientSession.getCurrentUser() == null) {
            balanceLabel.setText("0 VND");
            return;
        }
        setupTable();
        refreshBalanceUI();
        loadHistory();
    }

    /** Gắn cellValueFactory cho từng cột — chỉ gọi 1 lần trong initialize(). */
    private void setupTable() {
        colType.setCellValueFactory(row -> {
            String label = row.getValue().getType().equals("DEPOSIT") ? "⬆ Nạp tiền" : "⬇ Rút tiền";
            return new SimpleStringProperty(label);
        });
        colAmount.setCellValueFactory(row -> {
            String sign   = row.getValue().getType().equals("DEPOSIT") ? "+" : "-";
            String amount = VND_FMT.format(row.getValue().getAmount()) + " VND";
            return new SimpleStringProperty(sign + amount);
        });
        colTime.setCellValueFactory(row ->
                new SimpleStringProperty(row.getValue().getCreatedAt().format(TIME_FMT))
        );
    }

    /** Gọi server lấy lịch sử rồi đổ vào TableView. */
    private void loadHistory() {
        UserDTO user = ClientSession.getCurrentUser();
        if (user == null) return;

        BalanceResponse res = SocketClient.getInstance().getDepositHistory(user.getId());
        if (res != null && res.isSuccess() && res.getHistory() != null) {
            historyTable.getItems().setAll(res.getHistory());
        }
    }

    @FXML
    public void deposit() {
        depositFlow.setVisible(true);
        withdrawFlow.setVisible(false);
        confirmButton.setVisible(true);
        cancelButton.setVisible(true);
        isDeposit = true;
    }

    @FXML
    public void withdraw() {
        depositFlow.setVisible(false);
        withdrawFlow.setVisible(true);
        confirmButton.setVisible(true);
        cancelButton.setVisible(true);
        isDeposit = false;
    }

    @FXML
    public void handleConfirm() {
        hideError();

        UserDTO user = ClientSession.getCurrentUser();
        if (user == null) return;

        // Lấy số tiền từ input
        String amountStr = isDeposit ? depositField.getText() : withdrawField.getText();
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountStr.trim());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                showError("Số tiền phải lớn hơn 0!");
                return;
            }
        } catch (NumberFormatException e) {
            showError("Vui lòng nhập đúng số tiền (Ví dụ: 100000)!");
            return;
        }

        // Kiểm tra đủ tiền rút không (validate trước khi gửi server)
        if (!isDeposit && user.getBalance().compareTo(amount) < 0) {
            showError("Số dư không đủ để thực hiện rút tiền!");
            return;
        }

        // --- Gửi yêu cầu đến server qua socket ---
        String type = isDeposit ? "DEPOSIT" : "WITHDRAW";
        BalanceRequest  request  = new BalanceRequest(user.getId(), amount, type);
        BalanceResponse response = SocketClient.getInstance().updateBalance(request);

        if (response.isSuccess()) {
            // Cập nhật session với dữ liệu mới từ server
            ClientSession.setCurrentUser(response.getData());
            refreshBalanceUI();
            resetForm();
            loadHistory(); // cập nhật bảng lịch sử
            System.out.println(type + " thành công. Số dư mới: " + response.getData().getBalance());
        } else {
            showError(response.getMessage());
        }
    }

    @FXML
    public void handleCancel() {
        resetForm();
        hideError();
    }

    // ── Helper ────────────────────────────────────────────────

    private void refreshBalanceUI() {
        UserDTO user = ClientSession.getCurrentUser();
        if (user == null) return;
        NumberFormat nf = NumberFormat.getInstance(Locale.of("vi", "VN"));
        balanceLabel.setText(nf.format(user.getBalance()) + " VND");
    }

    private void resetForm() {
        depositField.clear();
        withdrawField.clear();
        depositFlow.setVisible(false);
        withdrawFlow.setVisible(false);
        confirmButton.setVisible(false);
        cancelButton.setVisible(false);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        withdrawField.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        withdrawField.setStyle("");
    }
}