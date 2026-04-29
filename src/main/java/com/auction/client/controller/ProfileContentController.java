package com.auction.client.controller;

import com.auction.server.dao.UserDAO;
import com.auction.server.model.User;
import com.auction.session.Session;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

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
    private void handleEdit() {
        if (editButton.getText().equals("Edit")) {
            // --- ĐANG LÀ EDIT: MỞ FORM CHO SỬA ---
            showEditMode(true);
            editButton.setText("Save Changes");
            cancelButton.setVisible(true);
            cancelButton.setManaged(true);

            User user = Session.getCurrentUser();
            nameField.setText(user.getUsername());
            locationField.setText(user.getLocation());
            desField.setText(user.getDescription());
            passField.setText(""); // luôn rỗng
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
        User user = Session.getCurrentUser();
        UserDAO userDAO = new UserDAO();
        try {
            // 🔥 update object trước
            user.setUsername(nameField.getText());
            user.setDescription(desField.getText());
            user.setLocation(locationField.getText());

            // 🔥 lưu DB (profile)
            userDAO.updateProfile(user);

            // 🔥 nếu có nhập password thì mới update
            if (!passField.getText().isEmpty()) {
                userDAO.updatePassword(user.getId(), passField.getText());
            }

            // 🔥 update UI sau khi DB OK
            nameLabel.setText(user.getUsername());
            desLabel.setText(user.getDescription());
            locationLabel.setText(user.getLocation());
            passLabel.setText("********");

            System.out.println("Đã lưu DB thành công!");

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Lỗi khi lưu DB");
            return;
        }

        showEditMode(false);
        editButton.setText("Edit");
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
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
        User user = Session.getCurrentUser();
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);

        if (user == null) return;

        // 🔥 load từ DB (qua Session)
        nameLabel.setText(user.getUsername());
        displayEmail.setText(user.getEmail());
        desLabel.setText(user.getDescription());
        locationLabel.setText(user.getLocation());
        passLabel.setText("********");

        passField.setText("");
    }
}
