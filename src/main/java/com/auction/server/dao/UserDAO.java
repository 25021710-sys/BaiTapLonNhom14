package com.auction.server.dao;
import com.auction.server.model.User;
import com.auction.server.config.DatabaseConnection;
import com.auction.server.model.UserRole;
import com.auction.server.util.PasswordUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;

public class UserDAO {

    public User register(String username, String password, String email) throws SQLException {
        String checkSql = "SELECT * FROM users WHERE email = ? OR username = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement checkPs = connection.prepareStatement(checkSql)) {
            checkPs.setString(1, email);
            checkPs.setString(2, username);
            ResultSet rs = checkPs.executeQuery();
            if (rs.next()) {
                System.out.println("Email hoặc username đã tồn tại!");
                return null;
            }
            String salt = PasswordUtil.generationSalt();
            String hash = PasswordUtil.hash(password, salt);
            String insertSql = "INSERT INTO users (username, password_hash, email, balance, role, active, salt, description, location) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement insertPs = connection.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS);
            insertPs.setString(1, username);
            insertPs.setString(2, hash);
            insertPs.setString(3, email);
            insertPs.setBigDecimal(4, BigDecimal.valueOf(0));
            insertPs.setString(5, UserRole.USER.name());
            insertPs.setBoolean(6, true);
            insertPs.setString(7, salt);
            insertPs.setString(8, "description");
            insertPs.setString(9, "location");


            int affectedRows = insertPs.executeUpdate();
            if (affectedRows > 0) {
                ResultSet keys = insertPs.getGeneratedKeys();
                if (keys.next()) {
                    int id = keys.getInt(1);
                    return new User(
                            id,
                            username,
                            hash,
                            email,
                            BigDecimal.valueOf(0),
                            UserRole.USER,
                            true,
                            java.time.LocalDateTime.now(),
                            salt,
                            "",
                            ""
                    );
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("ERROR: " + e.getMessage());
        }
        return null;
    }

    public User login(String email, String password) throws SQLException {
        String checkSql = "SELECT * FROM users WHERE email = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement checkPs = connection.prepareStatement(checkSql)) {
            checkPs.setString(1, email);
            ResultSet rs = checkPs.executeQuery();
            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                String salt = rs.getString("salt");
                // verify
                if (PasswordUtil.verify(password, salt, storedHash)) {
                    return new User(rs.getInt("id"),
                            rs.getString("username"),
                            storedHash,
                            rs.getString("email"),
                            rs.getBigDecimal("balance"),
                            UserRole.valueOf(rs.getString("role")),
                            rs.getBoolean("active"),
                            rs.getTimestamp("created_at").toLocalDateTime(), // lấy từ DB)
                            rs.getString("salt"),
                            rs.getString("description"),
                            rs.getString("location"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    public void updateBalance(int userId, BigDecimal newBalance) throws SQLException {
        String sql = "UPDATE users SET balance = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBigDecimal(1, newBalance);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }
    public void updateProfile(User user) throws SQLException {
        String sql = "UPDATE users SET username = ?, description = ?, location = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getUsername());
            ps.setString(2, user.getDescription());
            ps.setString(3, user.getLocation());
            ps.setInt(4, user.getId());

            int rows = ps.executeUpdate();

            if (rows == 0) {
                throw new SQLException("Không tìm thấy user để update!");
            }
        }
    }
    public void updatePassword(int userId, String newPassword) throws SQLException {
        String salt = PasswordUtil.generationSalt();
        String hash = PasswordUtil.hash(newPassword, salt);

        String sql = "UPDATE users SET password_hash = ?, salt = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, hash);
            ps.setString(2, salt);
            ps.setInt(3, userId);

            int rows = ps.executeUpdate();

            if (rows == 0) {
                throw new SQLException("Không tìm thấy user để update password!");
            }
        }
    }
}