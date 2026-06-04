package com.auction.client.controller;

import com.auction.client.MainApp;
import com.auction.client.network.SocketClient;
import com.auction.common.request.RegisterRequest;
import com.auction.common.response.RegisterResponse;
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
 * RegisterController - xử lý giao diện đăng ký.
 * Giao tiếp với server qua SocketClient.
 */
public class RegisterController {

    @FXML private Label         errorLabel;
    @FXML private TextField     emailField;
    @FXML private PasswordField passwordField;
    @FXML private TextField     usernameField;

    @FXML
    public void handleSignup(ActionEvent event) {
        String username = usernameField.getText().trim();
        String email    = emailField.getText().trim();
        String pass     = passwordField.getText();

        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";

        // reset UI
        errorLabel.setVisible(false);
        emailField.setStyle("");
        usernameField.setStyle("");
        passwordField.setStyle("");

        // --- Validation phía client ---
        if (username.isEmpty() || pass.isEmpty() || email.isEmpty()) {
            showError("Vui lòng nhập thông tin đầy đủ");
            return;
        }
        if (!email.matches(emailRegex)) {
            showError("Định dạng Email không hợp lệ!");
            return;
        }
        if (username.length() < 8 || pass.length() < 8) {
            showError("Tên người dùng và mật khẩu phải ít nhất 8 ký tự");
            usernameField.setStyle("-fx-border-color: red;");
            passwordField.setStyle("-fx-border-color: red;");
            return;
        }

        // --- Gửi yêu cầu đến server qua socket ---
        RegisterRequest  request  = new RegisterRequest(username, email, pass);
        RegisterResponse response = SocketClient.getInstance().register(request);

        if (response.isSuccess()) {
            System.out.println("Đăng ký thành công: " + username);
            goToLogin(event);
        } else {
            showError(response.getMessage());
            emailField.setStyle("-fx-border-color: red;");
        }
    }

    private void navigate(ActionEvent event,
                          String fxmlPath,
                          String title) {

        try {

            FXMLLoader loader =
                new FXMLLoader(getClass().getResource(fxmlPath));

            Parent root = loader.load();

            Stage stage =
                (Stage) ((Node) event.getSource())
                    .getScene()
                    .getWindow();

            Scene scene = new Scene(
                root,
                MainApp.WIDTH,
                MainApp.HEIGHT
            );

            stage.setScene(scene);
            stage.setTitle(title);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void goToLogin(ActionEvent event) {
        navigate(event,
            "/view/LoginView.fxml",
            "Login");
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        emailField.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
    }
}
