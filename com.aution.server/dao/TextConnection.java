package com.auction.server.dao;

public class TextConnection {
    public static void main(String[] args) {
        try {
            var conn = DBContext.getConnection();
            if (conn != null) System.out.println("Kết nối MySQL thành công rồi bạn ơi!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
