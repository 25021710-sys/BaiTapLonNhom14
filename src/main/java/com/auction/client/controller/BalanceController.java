package com.auction.client.controller;

import com.auction.common.dto.UserDTO;
import com.auction.session.Session;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.text.TextFlow;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public class BalanceController {

    @FXML private TextField depositField;
    @FXML private TextField withdrawField;
    @FXML private Button confirmButton;
    @FXML private Button cancelButton;
    @FXML private TextFlow depositFlow;
    @FXML private TextFlow withdrawFlow;
    @FXML private Label balanceLabel;
    @FXML private Label errorLabel;

    private boolean isDeposit = true;
    private UserDTO user;

    @FXML
    public void initialize() {
        user = Session.getCurrentUser();

        if (user == null) {
            balanceLabel.setText("0 VND");
            return;
        }
        updateBalanceUI();
    }
    private void updateBalanceUI() {
        UserDTO user = Session.getCurrentUser(); // lấy từ Session
        NumberFormat nf = NumberFormat.getInstance(Locale.of("vi", "VN"));
        balanceLabel.setText(nf.format(user.getBalance()) + " VND");
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
        try {
            hideError();

            // 1. Lấy user hiện tại từ Session (kiểu UserDTO)
            user = Session.getCurrentUser();
            if (user == null) return;

            // 2. Lấy số tiền người dùng nhập vào
            BigDecimal amount = new BigDecimal(isDeposit ? depositField.getText() : withdrawField.getText());

            // 3. Lấy số dư hiện tại từ UserDTO
            BigDecimal currentBalance = user.getBalance();
            BigDecimal newBalance;

            // 4. Tính toán số dư mới bằng BigDecimal
            if (isDeposit) {
                // newBalance = currentBalance + amount
                newBalance = currentBalance.add(amount);
            } else {
                // Kiểm tra nếu rút quá số dư
                if (currentBalance.compareTo(amount) < 0) {
                    returnError("Số dư không đủ để thực hiện rút tiền!");
                    return;
                }
                // newBalance = currentBalance - amount
                newBalance = currentBalance.subtract(amount);
            }

            // 5. Gửi yêu cầu cập nhật lên Server qua Socket (Thay thế cho UserDAO)
            // Lưu ý: Theo kiến trúc Client-Server, bạn gửi BalanceRequest chứa newBalance
            // Ví dụ: ClientNetwork.getInstance().send(new BalanceRequest(user.getId(), amount, isDeposit));

            /* ĐOẠN NÀY SAU KHI SERVER PHẢN HỒI THÀNH CÔNG */
            // Cập nhật lại UI sau khi tính toán xong
            updateBalanceUI();

            // Reset giao diện
            depositField.clear();
            withdrawField.clear();
            depositFlow.setVisible(false);
            withdrawFlow.setVisible(false);
            confirmButton.setVisible(false);
            cancelButton.setVisible(false);

        } catch (NumberFormatException e) {
            returnError("Vui lòng nhập đúng số tiền (Ví dụ: 100000)!");
        } catch (Exception e) {
            returnError("Đã xảy ra lỗi: " + e.getMessage());
        }
    }
    @FXML
    public void handleCancel() {
        // reset input
        depositField.clear();
        withdrawField.clear();

        // ẩn form
        depositFlow.setVisible(false);
        withdrawFlow.setVisible(false);

        // ẩn buttons
        confirmButton.setVisible(false);
        cancelButton.setVisible(false);

        // reset error
        hideError();
    }
    public void returnError(String message){
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        withdrawField.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
    }
    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        withdrawField.setStyle(""); // reset viền đỏ
    }
}
