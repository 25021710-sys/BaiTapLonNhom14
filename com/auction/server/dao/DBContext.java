package com.auction.server.dao;

import java.sql.Connection;
import java.sql.DriverManager;

public class DBContext {
    public static Connection getConnection() throws Exception {
        String url = "jdbc:mysql://metro.proxy.rlwy.net:25032/railway";
        String user = "root";
        String password = "clnqbcMyhShsstuEyhbjpEGwANVcpCbq";
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(url, user, password);
    }
}