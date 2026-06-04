package com.auction.client.controller;

import com.auction.client.MainApp;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class WelcomeController {

  public void goToLogin(ActionEvent event) throws IOException {

    Parent root = FXMLLoader.load(
        getClass().getResource("/view/LoginView.fxml"));

    Stage stage =
        (Stage) ((Node) event.getSource())
            .getScene()
            .getWindow();

    stage.setScene(
        new Scene(
            root,
            MainApp.WIDTH,
            MainApp.HEIGHT
        )
    );
    stage.setTitle("Login");
    stage.show();
  }

  public void goToRegister(ActionEvent event) throws IOException {

    Parent root = FXMLLoader.load(
        getClass().getResource("/view/RegisterView.fxml"));

    Stage stage =
        (Stage) ((Node) event.getSource())
            .getScene()
            .getWindow();

    stage.setScene(
        new Scene(
            root,
            MainApp.WIDTH,
            MainApp.HEIGHT
        )
    );
    stage.setTitle("Register");
    stage.show();
  }
}