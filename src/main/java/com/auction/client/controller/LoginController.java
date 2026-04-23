package com.auction.client.controller;
import com.auction.server.dao.UserDAO;
import com.auction.server.model.User;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;

public class LoginController {
    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;
    @FXML
    public void handleLogin(ActionEvent event) throws SQLException {
        String email = emailField.getText().trim();
        String pass = passwordField.getText().trim();
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        // Validation
        if (email.isEmpty() || pass.isEmpty()) {
            showError("Vui lòng nhập đầy đủ thông tin");
            return;
        } else if (!email.matches(emailRegex)){
            showError("Định dạng Email không hợp lệ (ví dụ: abc@gmail.com)");
            return;
        } else {
            UserDAO userDAO = new UserDAO();
            User user = userDAO.login(email, pass);
            if (user != null) {
                System.out.println("Đăng nhập thành công: " + user.getUsername());
                goToUserProfile(event);

            } else {
                showError("Sai mật khẩu hoặc email");
                passwordField.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
            }
        }
    }
    @FXML
    public void goToRegister(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/RegisterView.fxml")
            );
            Parent root = loader.load();

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            stage.setScene(new Scene(root));
            stage.setTitle("Sign Up");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @FXML
    public void goToDashBoard(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/DashBoardView.fxml")
            );
            Parent root = loader.load();

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            stage.setScene(new Scene(root));
            stage.setTitle("Go to DashBoard");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @FXML
    public void goToUserProfile(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/UserProfile.fxml")
            );
            Parent root = loader.load();

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            stage.setScene(new Scene(root));
            stage.setTitle("Go to UserProfile");

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
