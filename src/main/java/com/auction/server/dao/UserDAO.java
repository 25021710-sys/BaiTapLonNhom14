package com.auction.server.dao;
import com.auction.server.model.User;
import com.auction.server.config.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;

public class UserDAO {

    public User Register(String username, String email, String password) throws SQLException {
        String checkSql = "SELECT * FROM users WHERE username = ?";
        try (Connection connection = DatabaseConnection.getConnection();
        PreparedStatement checkPs = connection.prepareStatement(checkSql)){
            checkPs.setString(1, username);
            ResultSet rs = checkPs.executeQuery();
            if (rs.next()){
                return null;
            }
            String insertSql = "INSERT INTO users (username, password_hash, email, balance, role, active) VALUES (?, ?, ?, ?, ?, ?)";
            PreparedStatement insertPs = connection.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS);
            insertPs.setString(1, username);
            insertPs.setString(2, password); // sau này hash
            insertPs.setString(3, email);
            insertPs.setDouble(4, 0.0);
            insertPs.setString(5, "USER");
            insertPs.setBoolean(6, true);
            int affectedRows = insertPs.executeUpdate();

            if (affectedRows > 0) {
                ResultSet keys = insertPs.getGeneratedKeys();
                if (keys.next()) {
                    int id = keys.getInt(1);

                    return new User(
                            id,
                            java.time.LocalDateTime.now(),
                            username,
                            password,
                            email,
                            0.0,
                            "USER",
                            true
                    );
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
