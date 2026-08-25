package com.vcampus.server.service.impl;

import com.vcampus.common.vo.UserVO;
import com.vcampus.server.dao.UserDao;
import com.vcampus.server.dao.impl.UserDaoImpl;
import com.vcampus.server.service.UserService;

import java.sql.SQLException;

/**
 * 用户登录业务实现。
 */
public class UserServiceImpl implements UserService {

    private final UserDao userDao = new UserDaoImpl();

    /**
     * 登录鉴权，账号或密码为空直接判定失败。
     */
    @Override
    public UserVO login(String accountNumber, String password) {
        try {
            // 参数校验前置，避免向数据库提交空查询
            if (accountNumber == null || password == null) {
                return null;
            }
            return userDao.findByAccountAndPassword(accountNumber, password);
        } catch (SQLException e) {
            throw new RuntimeException("用户登录查询失败", e);
        }
    }
}
