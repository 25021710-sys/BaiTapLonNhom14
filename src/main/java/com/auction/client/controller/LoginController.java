package com.auction.client.controller;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {
    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;
    @FXML
    public void handleLogin(ActionEvent event){
        String email = emailField.getText();
        String pass = passwordField.getText();
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        // Validation
        if (email.isEmpty() || pass.isEmpty()) {
            errorLabel.setText("Vui lòng nhập đầy đủ thông tin");
            errorLabel.setVisible(true);
            return;
        } else if (!email.matches(emailRegex)){
            errorLabel.setText("Định dạng Email không hợp lệ (ví dụ: abc@gmail.com)");
            errorLabel.setVisible(true);
            emailField.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
            return;
        } else {
            goToDashBoard(event);
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
}
