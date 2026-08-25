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
 * 用户 JDBC 实现。
 */
public class UserDaoImpl implements UserDao {

    /**
     * 按账号和密码查询用户，并将权限编号转换为 UserRole。
     */
    @Override
    public UserVO findByAccountAndPassword(String accountNumber, String password) throws SQLException {
        String sql = "SELECT account_number, password, name, role_code FROM sys_user "
                + "WHERE account_number = ? AND password = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                // 登录成功时把数据库权限编码映射为客户端可直接使用的角色枚举
                if (rs.next()) {
                    UserVO user = new UserVO();
                    user.setAccountNumber(rs.getString("account_number"));
                    user.setPassword(rs.getString("password"));
                    user.setName(rs.getString("name"));
                    user.setRole(UserRole.fromJurisdiction(rs.getInt("role_code")));
                    return user;
                }
            }
        }
        return null;
    }
}
