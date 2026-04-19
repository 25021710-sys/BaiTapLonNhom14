package com.auction.client.controller;

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
    public void handleSignup(){
        String user = usernameField.getText();
        String email = emailField.getText();
        String pass = passwordField.getText();
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        // Validation
        if (user.isEmpty() || pass.isEmpty() || email.isEmpty()) {
            errorLabel.setText("Vui lòng nhập đầy đủ thông tin");
            errorLabel.setVisible(true);
            return;
        } else if (!email.matches(emailRegex)){
            errorLabel.setText("Định dạng Email không hợp lệ (ví dụ: abc@gmail.com)");
            errorLabel.setVisible(true);
            emailField.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
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
