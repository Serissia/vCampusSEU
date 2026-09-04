package com.vcampus.server.dao.impl;

import com.vcampus.common.vo.UserRole;
import com.vcampus.common.vo.UserVO;
import com.vcampus.server.dao.UserDao;
import com.vcampus.server.util.DBUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户 DAO 实现类
 *
 * @author Serissia
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

    @Override
    public boolean updatePassword(String uid, String newPassword) throws SQLException {
        String sql = "UPDATE tbl_user SET password = ? WHERE uid = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newPassword);
            pstmt.setString(2, uid);
            return pstmt.executeUpdate() > 0;
        }
    }

    @Override
    public boolean updateBalance(String uid, BigDecimal newBalance) throws SQLException {
        String sql = "UPDATE tbl_user SET balance = ? WHERE uid = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBigDecimal(1, newBalance);
            pstmt.setString(2, uid);
            return pstmt.executeUpdate() > 0;
        }
    }

    @Override
    public UserVO queryByUidForUpdate(Connection conn, String uid) throws SQLException {
        String sql = "SELECT uid, password, role, name, balance, status FROM tbl_user WHERE uid = ? FOR UPDATE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uid);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    UserVO user = new UserVO();
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
                    return user;
                }
            }
        }
        return null;
    }

    @Override
    public boolean deductBalance(Connection conn, String uid, BigDecimal amount) throws SQLException {
        String sql = "UPDATE tbl_user SET balance = balance - ? WHERE uid = ? AND balance >= ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBigDecimal(1, amount);
            pstmt.setString(2, uid);
            pstmt.setBigDecimal(3, amount);
            return pstmt.executeUpdate() > 0;
        }
    }

    @Override
    public boolean createUser(UserVO user) throws SQLException {
        String sql = "INSERT INTO tbl_user(uid, password, role, name, balance, status) VALUES (?, ?, ?, ?, 0, 1)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUid());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getRole().name());
            ps.setString(4, user.getName());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public List<UserVO> listAllUsers() throws SQLException {
        String sql = "SELECT uid, password, role, name, balance, status FROM tbl_user ORDER BY uid";
        List<UserVO> users = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UserVO user = new UserVO();
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
                users.add(user);
            }
        }
        return users;
    }

    @Override
    public boolean updateUserInfo(String oldUid, String newUid, String name, String role, String status) throws SQLException {
        String sql = "UPDATE tbl_user SET uid = ?, name = ?, role = ?, status = ? WHERE uid = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newUid);
            ps.setString(2, name);
            ps.setString(3, role);
            ps.setInt(4, Integer.parseInt(status));
            ps.setString(5, oldUid);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean deleteUser(String uid) throws SQLException {
        String sql = "DELETE FROM tbl_user WHERE uid = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uid);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean uidExists(String uid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM tbl_user WHERE uid = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
}
