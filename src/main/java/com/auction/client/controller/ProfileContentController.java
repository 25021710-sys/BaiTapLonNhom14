package com.auction.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class ProfileContentController {
    @FXML private Button cancelButton;
    @FXML private Button editButton;

    @FXML private Label passLabel;
    @FXML private PasswordField passField;

    @FXML private Label desLabel;
    @FXML private TextField desField;

    @FXML private Label nameLabel;
    @FXML private TextField nameField;

    @FXML private Label locationLabel;
    @FXML private TextField locationField;

    @FXML
    private void handleEdit() {
        if (editButton.getText().equals("Edit")) {
            // --- ĐANG LÀ EDIT: MỞ FORM CHO SỬA ---
            showEditMode(true);
            editButton.setText("Save Changes");
            cancelButton.setVisible(true);
            cancelButton.setManaged(true);
            nameField.setText(nameLabel.getText());
            locationField.setText(locationLabel.getText());
            passField.setText(passLabel.getText());
            desField.setText(desLabel.getText());
        } else {
            // --- ĐANG LÀ SAVE: LƯU VÀ ĐÓNG FORM ---
            handleSave();
        }
    }
    @FXML
    private void handleCancel(){
        showEditMode(false);
        editButton.setText("Edit");
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
    }
    @FXML
    private void handleSave(){
        // 1. Lấy dữ liệu từ TextField cập nhật ngược lại cho Label
        nameLabel.setText(nameField.getText());
        passLabel.setText(passField.getText());
        desLabel.setText(desField.getText());
        locationLabel.setText(locationField.getText());
        // (Chỗ này bạn có thể viết thêm code để lưu vào Database)

        // 2. Quay lại chế độ xem (Giống hệt lúc bấm Cancel)
        showEditMode(false);

        // 3. Đổi tên nút về lại ban đầu
        editButton.setText("Edit");
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);

        System.out.println("Đã lưu thành công!");
    }
    public void showEditMode(boolean mode) {
        nameLabel.setVisible(!mode);
        nameLabel.setManaged(!mode);
        nameField.setVisible(mode);
        nameField.setManaged(mode);

        passLabel.setVisible(!mode);
        passLabel.setManaged(!mode);
        passField.setVisible(mode);
        passField.setManaged(mode);

        desLabel.setVisible(!mode);
        desLabel.setManaged(!mode);
        desField.setVisible(mode);
        desField.setManaged(mode);

        locationLabel.setVisible(!mode);
        locationLabel.setManaged(!mode);
        locationField.setVisible(mode);
        locationField.setManaged(mode);
    }
}
