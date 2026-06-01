package com.auction.server.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * DatabaseConnection – Connection Pool với HikariCP.
 *
 * VẤN ĐỀ CŨ:
 *   Dùng DriverManager.getConnection() trực tiếp → mỗi câu SQL mở 1 TCP connection mới
 *   đến TiDB Cloud (round-trip ~100–300ms). Với N queries/request, tổng độ trễ
 *   tăng theo hệ số N → timeout 10s dễ bị vượt.
 *
 * FIX:
 *   HikariCP giữ sẵn pool kết nối tái sử dụng → lấy connection ~0ms thay vì ~200ms.
 *   minimumIdle=2, maximumPoolSize=10 phù hợp với TiDB Cloud free tier.
 */
public class DatabaseConnection {

    private static final String URL      = "jdbc:mysql://gateway01.ap-southeast-1.prod.aws.tidbcloud.com:4000/railway"
            + "?serverTimezone=Asia/Ho_Chi_Minh" // múi giờ
            + "&useSSL=true"                     // mã hoá kết nối
            + "&cachePrepStmts=true"             // cache của câu SQL đã compile
            + "&prepStmtCacheSize=250"           // cache tối đa 250 câu
            + "&prepStmtCacheSqlLimit=2048";     // câu SQL tối đa 2048 ký tự
    private static final String DB_USER  = "2da7P72yRjHDRu2.root";
    private static final String PASSWORD = "dwxofeHifpCuB53s";

    private static final HikariDataSource DATA_SOURCE;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL);
        config.setUsername(DB_USER);
        config.setPassword(PASSWORD);

        // Pool size: đủ cho 100 client đồng thời nhưng không làm quá tải TiDB
        config.setMinimumIdle(2);
        config.setMaximumPoolSize(10);

        // Timeout lấy connection từ pool (ms)
        config.setConnectionTimeout(5_000);

        // Thời gian giữ connection nhàn rỗi trước khi đóng (ms)
        config.setIdleTimeout(300_000);   // 5 phút

        // Thời gian tối đa một connection tồn tại — tránh stale connection
        config.setMaxLifetime(1_800_000); // 30 phút

        // Kiểm tra connection còn sống trước khi trả về
        config.setConnectionTestQuery("SELECT 1");

        config.setPoolName("AuctionPool");

        DATA_SOURCE = new HikariDataSource(config);
    }

    /** Trả về một connection từ pool. Nhớ đóng bằng try-with-resources. */
    public static Connection getConnection() throws SQLException {
        return DATA_SOURCE.getConnection();
    }

    /** Gọi khi shutdown server để đóng pool sạch sẽ. */
    public static void close() {
        if (DATA_SOURCE != null && !DATA_SOURCE.isClosed()) {
            DATA_SOURCE.close();
        }
    }
}