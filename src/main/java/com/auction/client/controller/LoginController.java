package com.auction.client.controller;

import com.auction.client.network.SocketClient;
import com.auction.common.dto.UserDTO;
import com.auction.common.request.LoginRequest;
import com.auction.common.response.LoginResponse;
import com.auction.client.session.ClientSession;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * LoginController - xử lý giao diện đăng nhập.
 * Giao tiếp với server qua SocketClient (không gọi trực tiếp AuthService nữa).
 */
public class LoginController {

    @FXML private TextField     emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label         errorLabel;

    private static final double APP_WIDTH = 1200;
    private static final double APP_HEIGHT = 750;

    @FXML
    public void handleLogin(ActionEvent event) {
        String email = emailField.getText().trim();
        String pass  = passwordField.getText().trim();

        // --- Validation phía client ---
        if (email.isEmpty() || pass.isEmpty()) {
            showError("Vui lòng nhập đầy đủ thông tin");
            return;
        }
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        if (!email.matches(emailRegex)) {
            showError("Định dạng Email không hợp lệ");
            return;
        }

        // --- Gửi yêu cầu đến server qua socket ---
        LoginRequest  request  = new LoginRequest(email, pass);
        LoginResponse response = SocketClient.getInstance().login(request);

        if (response.isSuccess()) {
            UserDTO user = response.getUser();
            System.out.println("Đăng nhập thành công: " + user.getUsername());

            // Lưu vào Session (DTO, không phải Entity)
            ClientSession.setCurrentUser(user);
            goToDashBoard(event);

        } else {
            showError(response.getMessage());
            passwordField.setStyle("-fx-border-color: red;");
        }
    }

    @FXML
    public void goToRegister(ActionEvent event) {
        navigate(event, "/view/RegisterView.fxml", "Sign Up");
    }

    @FXML
    public void goToDashBoard(ActionEvent event) {
        navigate(event, "/view/DashBoardView.fxml", "Dashboard");
    }

    @FXML
    public void goToUserProfile(ActionEvent event) {
        navigate(event, "/view/UserProfile.fxml", "Hồ sơ cá nhân");
    }

    // ── Helper ────────────────────────────────────────────────

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        emailField.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
    }

    private void navigate(ActionEvent event, String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            Scene scene = new Scene(root, APP_WIDTH, APP_HEIGHT);

            stage.setScene(scene);
            stage.setTitle(title);

            stage.setWidth(APP_WIDTH);
            stage.setHeight(APP_HEIGHT);
            stage.centerOnScreen();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
