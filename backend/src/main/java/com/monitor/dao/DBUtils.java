package com.monitor.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;


public class DBUtils {
    private static HikariDataSource dataSource;

    static {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://localhost:3306/ai_usage_monitor?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=utf-8");
            config.setUsername("root");
            config.setPassword("");
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");


            config.setMaximumPoolSize(15);
            config.setMinimumIdle(3);
            config.setIdleTimeout(30000);
            config.setConnectionTimeout(5000);
            config.setMaxLifetime(1800000);

            dataSource = new HikariDataSource(config);
        } catch (Exception e) {
            System.err.println("初始化 HikariCP 数据库连接池失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("初始化数据库连接池失败", e);
        }
    }


    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }


    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
