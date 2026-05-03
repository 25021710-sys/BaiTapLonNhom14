package com.auction.client.controller;

import com.auction.client.network.SocketClient;
import com.auction.common.dto.UserDTO;
import com.auction.common.request.UpdateProfileRequest;
import com.auction.common.response.UpdateProfileResponse;
import com.auction.session.Session;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * ProfileContentController - xử lý giao diện chỉnh sửa hồ sơ.
 * Giao tiếp với server qua SocketClient.
 */
public class ProfileContentController {

    @FXML private Label         displayEmail;
    @FXML private Button        cancelButton;
    @FXML private Button        editButton;

    @FXML private Label         passLabel;
    @FXML private PasswordField passField;

    @FXML private Label         desLabel;
    @FXML private TextField     desField;

    @FXML private Label         nameLabel;
    @FXML private TextField     nameField;

    @FXML private Label         locationLabel;
    @FXML private TextField     locationField;

    @FXML
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

    @FXML
    private void handleEdit() {
        if ("Edit".equals(editButton.getText())) {
            // Mở form chỉnh sửa
            showEditMode(true);
            editButton.setText("Save Changes");
            cancelButton.setVisible(true);
            cancelButton.setManaged(true);

            // Điền sẵn giá trị hiện tại
            UserDTO user = Session.getCurrentUser();
            nameField.setText(user.getUsername());
            locationField.setText(user.getLocation());
            desField.setText(user.getDescription());
        } else {
            // Lưu thay đổi
            handleSave();
        }
    }

    @FXML
    private void handleCancel() {
        showEditMode(false);
        editButton.setText("Edit");
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
    }

    @FXML
    private void handleSave() {
        UserDTO user = Session.getCurrentUser();
        if (user == null) return;

        // --- Gửi yêu cầu đến server qua socket ---
        UpdateProfileRequest request = new UpdateProfileRequest(
                user.getId(),
                nameField.getText(),
                desField.getText(),
                locationField.getText(),
                passField.getText()
        );

        UpdateProfileResponse response = SocketClient.getInstance().updateProfile(request);

        if (!response.isSuccess()) {
            System.err.println("Lỗi update profile: " + response.getMessage());
            return;
        }

        // Cập nhật Session với dữ liệu mới từ server
        Session.setCurrentUser(response.getUser());
        UserDTO updated = response.getUser();

        // Cập nhật UI
        nameLabel.setText(updated.getUsername());
        desLabel.setText(updated.getDescription());
        locationLabel.setText(updated.getLocation());
        passLabel.setText("********");

        System.out.println("Cập nhật profile thành công!");

        showEditMode(false);
        editButton.setText("Edit");
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
    }

    private void showEditMode(boolean editing) {
        nameLabel.setVisible(!editing);    nameLabel.setManaged(!editing);
        nameField.setVisible(editing);     nameField.setManaged(editing);

        passLabel.setVisible(!editing);    passLabel.setManaged(!editing);
        passField.setVisible(editing);     passField.setManaged(editing);

        desLabel.setVisible(!editing);     desLabel.setManaged(!editing);
        desField.setVisible(editing);      desField.setManaged(editing);

        locationLabel.setVisible(!editing); locationLabel.setManaged(!editing);
        locationField.setVisible(editing);  locationField.setManaged(editing);
    }
}
