package com.auction.client.controller;

import com.auction.server.dao.UserDAO;
import com.auction.session.Session;
import com.auction.server.model.User;
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
    @FXML private Button ConfirmButton;
    @FXML private TextFlow depositFlow;
    @FXML private TextFlow withdrawFlow;
    @FXML private Label balanceLabel;
    @FXML private Label errorLabel;

    private boolean isDeposit = true;
    private User user;

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
        User user = Session.getCurrentUser(); // lấy từ Session
        NumberFormat nf = NumberFormat.getInstance(Locale.of("vi", "VN"));
        balanceLabel.setText(nf.format(user.getBalance()) + " VND");
    }
    @FXML
    public void deposit() {
        depositFlow.setVisible(true);
        withdrawFlow.setVisible(false);
        ConfirmButton.setVisible(true);
        isDeposit = true;
    }

    @FXML
    public void withdraw() {
        depositFlow.setVisible(false);
        withdrawFlow.setVisible(true);
        ConfirmButton.setVisible(true);
        isDeposit = false;
    }
    @FXML
    public void handleConfirm() {
        try {
            hideError();

            User user = Session.getCurrentUser();
            BigDecimal amount;

            if (isDeposit) {
                amount = new BigDecimal(depositField.getText());
                user.deposit(amount); // ✅ dùng method trong User
            } else {
                amount = new BigDecimal(withdrawField.getText());
                user.withdraw(amount); // ✅ dùng method trong User
            }

            // 👉 lấy balance mới
            BigDecimal newBalance = user.getBalance();

            // 👉 update DB
            UserDAO dao = new UserDAO();
            dao.updateBalance(user.getId(), newBalance);

            // 👉 update UI
            updateBalanceUI();

            // 👉 reset UI
            depositField.clear();
            withdrawField.clear();
            depositFlow.setVisible(false);
            withdrawFlow.setVisible(false);
            ConfirmButton.setVisible(false);

        } catch (IllegalArgumentException | IllegalStateException e) {
            // ❗ lỗi từ deposit/withdraw
            returnError(e.getMessage());
        } catch (Exception e) {
            returnError("Nhập sai định dạng!");
        }
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
