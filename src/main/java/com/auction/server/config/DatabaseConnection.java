package com.auction.server.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    // 1. Khai báo 1 biến duy nhất để chứa "ống nước" (kết nối)
    private static Connection connection = null;

    // 2. Thông tin "địa chỉ" của vòi nước Railway (bạn giữ nguyên phần URL và USER)
    private static final String URL = "jdbc:mysql://metro.proxy.rlwy.net:25032/railway";
    private static final String USER = "root";
    private static final String PASSWORD = "dwxofeHifpCuB53s";
            ; // <--- BẠN SỬA DÒNG NÀY

    // 3. Đặt hàm khởi tạo là 'private' -> Ngăn chặn người khác tạo thêm ống nước mới bừa bãi
    private DatabaseConnection() {
    }

    // 4. Đây là cái "công tắc" duy nhất để mọi nơi trong code gọi đến khi cần dùng Database
    public static Connection getConnection() {
        try {
            // Kiểm tra: Nếu chưa có ống nước nào, hoặc ống cũ bị đứt -> Mới tạo kết nối
            if (connection == null || connection.isClosed()) {
                // Khai báo việc sử dụng "người phiên dịch" MySQL
                Class.forName("com.mysql.cj.jdbc.Driver");

                // Bắt đầu cắm ống nước tới máy chủ
                connection = DriverManager.getConnection(URL, USER, PASSWORD);
                System.out.println("Đã kết nối thành công tới Database Railway!");
            }
        } catch (ClassNotFoundException | SQLException e) {
            System.out.println("Lỗi kết nối Database: " + e.getMessage());
        }

        // Trả về cái ống nước đó (dù là mới tạo hay là đồ dùng lại)
        return connection;
    }
}