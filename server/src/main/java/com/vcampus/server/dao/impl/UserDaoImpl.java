package com.vcampus.server.dao.impl;

import com.vcampus.common.vo.UserRole;
import com.vcampus.common.vo.UserVO;
import com.vcampus.server.dao.UserDao;
import com.vcampus.server.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 用户数据访问 JDBC 实现
 *
 * @author Serissia
 * @version 1.0
 */
public class UserDaoImpl implements UserDao {

    @Override
    public UserVO login(String uid, String password) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        UserVO user = null;

        String sql = "SELECT uid, password, role, name, balance, status FROM tbl_user " +
                "WHERE uid = ? AND password = ? AND status = 1";

        try {
            conn = DBUtil.getConnection();
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, uid);
            pstmt.setString(2, password);

            rs = pstmt.executeQuery();
            if (rs.next()) {
                user = new UserVO();
                user.setUid(rs.getString("uid"));
                user.setPassword(rs.getString("password"));
                user.setName(rs.getString("name"));
                user.setBalance(rs.getBigDecimal("balance"));
                user.setStatus(rs.getInt("status"));

                try {
                    user.setRole(UserRole.valueOf(rs.getString("role")));
                } catch (Exception e) {
                    user.setRole(UserRole.STUDENT);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DBUtil.close(conn, pstmt, rs);
        }
        return user;
    }

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
                user.setName(rs.getString("name"));
                user.setBalance(rs.getBigDecimal("balance"));
                user.setStatus(rs.getInt("status"));
                try {
                    user.setRole(UserRole.valueOf(rs.getString("role")));
                } catch (Exception e) {
                    user.setRole(UserRole.STUDENT);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DBUtil.close(conn, pstmt, rs);
        }
        return user;
    }
}