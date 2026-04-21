package com.auction.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class ProfileContentController {
    @FXML
    private Button cancelButton;
    @FXML
    private Button editButton;
    @FXML
    private Label displayLocation;
    @FXML
    private Label displayEmail;
    @FXML
    private Label displayName;
    @FXML
    private Label displayDesc;
    @FXML
    private Label nameLabel;
    @FXML
    private TextField nameField;
    @FXML
    private void handleEdit() {
        // 1. Chuyển các Label thành TextField hoặc mở khóa TextField
        nameLabel.setVisible(false);
        nameLabel.setManaged(false); // Quan trọng: Để Label không chiếm chỗ nữa

        nameField.setVisible(true);
        nameField.setManaged(true);  // Quan trọng: Để TextField chiếm chỗ của Label
        nameField.setText(nameLabel.getText()); // Copy dữ liệu cũ sang ô nhập

        // 2. Đổi nút Edit thành nút Save
        editButton.setText("Save Changes");

        // 3. Hiện nút Cancel (nếu bạn có chuẩn bị sẵn)
        cancelButton.setVisible(true);
    }
    @FXML
    private void handleCancel(){

    }
}
