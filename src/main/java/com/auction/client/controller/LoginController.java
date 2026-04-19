package com.auction.client.controller;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.stage.Stage;
public class LoginController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;
    @FXML
    public void handleLogin(){
        String user = usernameField.getText();
        String pass = passwordField.getText();
        // Validation
        if (user.isEmpty() || pass.isEmpty()) {
            errorLabel.setText("Vui lòng nhập đầy đủ thông tin");
            errorLabel.setVisible(true);
            return;
        }
    }
    @FXML
    public void goToRegister(){
        System.out.println("Go to Register");
    }
}
