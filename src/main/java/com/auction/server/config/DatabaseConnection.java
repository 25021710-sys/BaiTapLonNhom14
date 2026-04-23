package com.auction.server.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    // 1. Khai báo 1 biến duy nhất để kết nối
    private static Connection connection = null;

    // 2. Thông tin "địa chỉ" của Railway
    private static final String URL = "jdbc:mysql://gateway01.ap-southeast-1.prod.aws.tidbcloud.com:4000/railway";
    private static final String USER = "2da7P72yRjHDRu2.root";
    private static final String PASSWORD = "dwxofeHifpCuB53s";

    // 3. Đặt hàm khởi tạo là 'private' -> Ngăn chặn người khác tạo thêm
    private DatabaseConnection() {
    }

    // 4. Đây là cái "công tắc" duy nhất để mọi nơi trong code gọi đến khi cần dùng Database
    public static Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                // Khai báo việc sử dụng "người phiên dịch" MySQL
                Class.forName("com.mysql.cj.jdbc.Driver");

                connection = DriverManager.getConnection(URL, USER, PASSWORD);
                System.out.println("Đã kết nối thành công tới Database Railway!");
            }
        } catch (ClassNotFoundException | SQLException e) {
            System.out.println("Lỗi kết nối Database: " + e.getMessage());
        }
        return connection;
    }
}