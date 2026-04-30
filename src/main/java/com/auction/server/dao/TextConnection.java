package com.auction.server.dao;

import com.auction.server.model.User;

public class TextConnection {
    public static void main(String[] args) {
        try {
            UserDAO userDAO = new UserDAO();
            // Thử đăng ký một user thật sự
            User newUser = userDAO.register("test_user", "123456", "test@gmail.com");

            if (newUser != null) {
                System.out.println("Đã thêm user thành công! ID: " + newUser.getId());
            } else {
                System.out.println("Thêm thất bại (có thể trùng username).");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
