package com.auction.server.dao;
import com.auction.server.model.User;
import com.auction.server.config.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;

public class UserDAO {

    public User register(String username, String password, String email) throws SQLException {
        String checkSql = "SELECT * FROM users WHERE email = ?";
        try (Connection connection = DatabaseConnection.getConnection();
        PreparedStatement checkPs = connection.prepareStatement(checkSql)){
            checkPs.setString(1, email);
            ResultSet rs = checkPs.executeQuery();
            if (rs.next()){
                System.out.println("Email đã tồn tại!");
                return null;
            }
            String insertSql = "INSERT INTO users (username, password_hash, email, balance, role, active) VALUES (?, ?, ?, ?, ?, ?)";
            PreparedStatement insertPs = connection.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS);
            insertPs.setString(1, username);
            insertPs.setString(2, password);// sau này hash
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
                            username,
                            password,
                            email,
                            0.0,
                            "USER",
                            true,
                            java.time.LocalDateTime.now()
                    );
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("ERROR: " + e.getMessage());
        }
        return null;
    }
}
