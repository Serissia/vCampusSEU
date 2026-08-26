package com.vcampus.server.dao.impl;

import com.vcampus.server.dao.UserDao;
import com.vcampus.server.util.DBUtil;
import com.vcampus.vo.UserVO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 用户数据访问接口 JDBC 实现类
 *
 * @author Serissia
 */
public class UserDaoImpl implements UserDao {

    /**
     * 用户登录验证并返回用户信息。
     * 使用 uid/password/role 在 tbl_user 表中查询用户，且仅在 status = 1（账号正常）时返回。
     * 方法内部捕获 SQLException；发生数据库错误或未找到时返回 null。
     * 注意：当前实现中密码为明文匹配（若数据库存储哈希，应在调用方进行比对）。
     *
     * @param uid      用户标识（学号/工号）
     * @param password 登录密码
     * @param role     用户角色
     * @return 匹配的 UserVO 对象；未找到或发生错误时返回 null
     */
    @Override
    public UserVO login(String uid, String password, String role) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        UserVO user = null;

        String sql = "SELECT uid, password, role, name, balance, status FROM tbl_user " +
                "WHERE uid = ? AND password = ? AND role = ? AND status = 1";

        try {
            conn = DBUtil.getConnection();
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, uid);
            pstmt.setString(2, password);
            pstmt.setString(3, role);

            rs = pstmt.executeQuery();
            if (rs.next()) {
                user = new UserVO();
                user.setUid(rs.getString("uid"));
                user.setPassword(rs.getString("password"));
                user.setRole(rs.getString("role"));
                user.setName(rs.getString("name"));
                user.setBalance(rs.getBigDecimal("balance"));
                user.setStatus(rs.getInt("status"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DBUtil.close(conn, pstmt, rs);
        }

        return user;
    }

    /**
     * 根据 uid 查询用户完整信息。
     * 执行简单的 SELECT 查询以获取用户字段。内部会捕获 SQLException；发生异常或未找到时返回 null。
     *
     * @param uid 用户标识（学号/工号）
     * @return 查询到的 UserVO 对象；未找到或发生错误时返回 null
     */
    @Override
    public UserVO queryByUid(String uid) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        UserVO user = null;

        String sql = "SELECT uid, password, role, name, balance, status FROM tbl_user WHERE uid = ?";

        try {
            conn = DBUtil.getConnection();
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, uid);

            rs = pstmt.executeQuery();
            if (rs.next()) {
                user = new UserVO();
                user.setUid(rs.getString("uid"));
                user.setPassword(rs.getString("password"));
                user.setRole(rs.getString("role"));
                user.setName(rs.getString("name"));
                user.setBalance(rs.getBigDecimal("balance"));
                user.setStatus(rs.getInt("status"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DBUtil.close(conn, pstmt, rs);
        }

        return user;
    }
}