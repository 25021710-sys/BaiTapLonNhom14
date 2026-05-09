package com.auction.client.controller;

import com.auction.client.network.SocketClient;
import com.auction.common.dto.UserDTO;
import com.auction.common.request.BalanceRequest;
import com.auction.common.response.BalanceResponse;
import com.auction.client.session.ClientSession;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.text.TextFlow;

import java.math.BigDecimal;
import java.text.NumberFormat;
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

    private boolean isDeposit = true;

    @FXML
    public void initialize() {
        if (ClientSession.getCurrentUser() == null) {
            balanceLabel.setText("0 VND");
            return;
        }
        refreshBalanceUI();
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
