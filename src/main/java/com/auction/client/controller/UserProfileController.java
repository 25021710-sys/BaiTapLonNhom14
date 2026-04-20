package com.auction.client.controller;

import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;

public class UserProfileController {
    @FXML
    private ImageView userAvatar;
    @FXML
    private ImageView userLogo;

    public void initialize(){
        Image image = new Image("https://static.vecteezy.com/system/resources/previews/046/010/545/non_2x/user-icon-simple-design-free-vector.jpg");
        userLogo.setImage(image);

    }
}
