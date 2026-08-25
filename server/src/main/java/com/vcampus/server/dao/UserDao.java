package com.vcampus.server.dao;

import com.vcampus.common.vo.UserVO;

import java.sql.SQLException;

/**
 * 用户数据访问接口。
 */
public interface UserDao {

    /**
     * 根据账号和密码查询用户。
     */
    UserVO findByAccountAndPassword(String accountNumber, String password) throws SQLException;
}
