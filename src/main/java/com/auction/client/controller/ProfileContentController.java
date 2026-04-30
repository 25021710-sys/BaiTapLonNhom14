package com.auction.client.controller;

import com.auction.common.dto.UserDTO;
import com.auction.common.request.UpdateProfileRequest;
import com.auction.common.response.UpdateProfileResponse;
import com.auction.server.service.AuthService;
import com.auction.session.Session;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.sql.SQLException;

public class ProfileContentController {
    @FXML private Label displayEmail;
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
    private void handleEdit(){
        if (editButton.getText().equals("Edit")) {
            // --- ĐANG LÀ EDIT: MỞ FORM CHO SỬA ---
            showEditMode(true);
            editButton.setText("Save Changes");
            cancelButton.setVisible(true);
            cancelButton.setManaged(true);

            UserDTO user = Session.getCurrentUser();

            nameField.setText(user.getUsername());
            locationField.setText(user.getLocation());
            desField.setText(user.getDescription());
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
    private void handleSave() {
        try {
            UserDTO user = Session.getCurrentUser();

            AuthService authService = new AuthService();

            UpdateProfileRequest request = new UpdateProfileRequest(
                    user.getId(),
                    nameField.getText(),
                    desField.getText(),
                    locationField.getText(),
                    passField.getText()
            );

            UpdateProfileResponse response = authService.updateProfile(request);

            if (!response.isSuccess()) {
                System.out.println(response.getMessage());
                return;
            }

            // 🔥 update lại session bằng DTO mới
            Session.setCurrentUser(response.getUser());

            UserDTO updatedUser = response.getUser();

            // update UI
            nameLabel.setText(updatedUser.getUsername());
            desLabel.setText(updatedUser.getDescription());
            locationLabel.setText(updatedUser.getLocation());
            passLabel.setText("********");

            System.out.println("Update thành công!");

            showEditMode(false);
            editButton.setText("Edit");
            cancelButton.setVisible(false);
            cancelButton.setManaged(false);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Lỗi khi update profile!");
        }
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
    public void initialize() {
        UserDTO user = Session.getCurrentUser();

        cancelButton.setVisible(false);
        cancelButton.setManaged(false);

        if (user == null) return;

        nameLabel.setText(user.getUsername());
        displayEmail.setText(user.getEmail());
        desLabel.setText(user.getDescription());
        locationLabel.setText(user.getLocation());
        passLabel.setText("********");

        passField.setText("");
    }
}
