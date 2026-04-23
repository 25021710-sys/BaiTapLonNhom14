package com.auction.client.controller;

import com.auction.server.dao.UserDAO;
import com.auction.server.model.User;
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

        // Reset style và ẩn error trước khi check
        errorLabel.setVisible(false);
        emailField.setStyle("");

        // Validation
        if (user.isEmpty() || pass.isEmpty() || email.isEmpty()) {
            errorLabel.setText("Vui lòng nhập đầy đủ thông tin");
            errorLabel.setVisible(true);
            return;
        }

        if (!email.matches(emailRegex)) {
            errorLabel.setText("Định dạng Email không hợp lệ!");
            errorLabel.setVisible(true);
            emailField.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
            return;
        }

        try {
            UserDAO userDAO = new UserDAO();
            // LƯU Ý: Đảm bảo thứ tự (user, pass, email) khớp với định nghĩa trong UserDAO
            User newUser = userDAO.register(user, pass, email);

            if (newUser != null) {
                System.out.println("Đăng ký thành công: " + user);
                goToLogin(event);
            } else {
                // newUser = null thường là do trùng Email (dựa trên code UserDAO của bạn)
                errorLabel.setText("Email này đã được sử dụng!");
                errorLabel.setVisible(true);
                emailField.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
            }
        } catch (Exception e) {
            // In ra lỗi cụ thể để debug
            System.err.println("Lỗi Registration: " + e.getMessage());
            e.printStackTrace();

            if (e.getMessage().contains("Duplicate entry")) {
                errorLabel.setText("Tên đăng nhập hoặc Email đã tồn tại!");
            } else {
                errorLabel.setText("Lỗi kết nối Database!");
            }
            errorLabel.setVisible(true);
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
}
