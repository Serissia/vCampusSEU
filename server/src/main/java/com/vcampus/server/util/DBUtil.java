package com.vcampus.server.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * 数据库连接管理与资源释放工具类
 * @author Serissia
 */
public class DBUtil {

    /** 数据库驱动类全限定名 */
    private static String driver;

    /** 数据库连接 URL */
    private static String url;

    /** 数据库访问用户名 */
    private static String username;

    /** 数据库访问密码 */
    private static String password;

    static {
        try (InputStream in = DBUtil.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in == null) {
                throw new RuntimeException("未能找到 db.properties 配置文件");
            }
            Properties props = new Properties();
            props.load(in);

            driver = props.getProperty("db.driver");
            url = props.getProperty("db.url");
            username = props.getProperty("db.username");
            password = props.getProperty("db.password");

            Class.forName(driver);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ExceptionInInitializerError("加载 db.properties 失败: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new ExceptionInInitializerError("未找到 MySQL 驱动类: " + e.getMessage());
        }
    }

    /**
     * 获取数据库连接
     *
     * @return 数据库连接 Connection 实例
     * @throws SQLException 数据库连接异常
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    /**
     * 统一释放数据库操作相关资源
     *
     * @param conn 数据库连接对象
     * @param stmt SQL 语句执行对象
     * @param rs   查询结果集对象
     */
    public static void close(Connection conn, Statement stmt, ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 统一释放非查询操作相关资源
     *
     * @param conn 数据库连接对象
     * @param stmt SQL 语句执行对象
     */
    public static void close(Connection conn, Statement stmt) {
        close(conn, stmt, null);
    }
}