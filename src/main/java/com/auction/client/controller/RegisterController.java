package com.auction.client.controller;

import com.auction.common.request.RegisterRequest;
import com.auction.common.response.RegisterResponse;
import com.auction.server.dao.UserDAO;
import com.auction.server.model.User;
import com.auction.server.service.AuthService;
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

public class RegisterController {
    @FXML
    private Label errorLabel;
    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField usernameField;
    @FXML
    public void handleSignup(ActionEvent event) {
        String user = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String pass = passwordField.getText();

        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";

        // reset UI
        errorLabel.setVisible(false);
        emailField.setStyle("");
        usernameField.setStyle("");
        passwordField.setStyle("");

        // Validation
        if (user.isEmpty() || pass.isEmpty() || email.isEmpty()) {
            showError("Vui lòng nhập thông tin đầy đủ");
            return;
        }

        if (!email.matches(emailRegex)) {
            showError("Định dạng Email không hợp lệ!");
            return;
        }

        if (user.length() < 8 || pass.length() < 8) {
            showError("Tên người dùng và mật khẩu phải ít nhất 8 ký tự");
            usernameField.setStyle("-fx-border-color: red;");
            passwordField.setStyle("-fx-border-color: red;");
            return;
        }

        try {
            // Dùng service
            AuthService authService = new AuthService();

            RegisterRequest request = new RegisterRequest(user, email, pass);
            RegisterResponse response = authService.register(request);

            if (response.isSuccess()) {
                System.out.println("Đăng ký thành công: " + user);
                goToLogin(event);

            } else {
                showError(response.getMessage());
                emailField.setStyle("-fx-border-color: red;");
            }

        } catch (Exception e) {
            e.printStackTrace();
            showError("Lỗi hệ thống, thử lại sau!");
        }
    }
    @FXML
    public void goToLogin(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/LoginView.fxml")
            );
            Parent root = loader.load();

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            stage.setScene(new Scene(root));
            stage.setTitle("Login");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        emailField.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
    }
}
