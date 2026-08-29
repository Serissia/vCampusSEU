package com.vcampus.server.service.impl;

import com.vcampus.common.vo.UserVO;
import com.vcampus.server.dao.UserDao;
import com.vcampus.server.dao.impl.UserDaoImpl;
import com.vcampus.server.service.UserService;

import java.math.BigDecimal;
import java.sql.SQLException;

/**
 * 用户业务接口实现
 *
 * @author Serissia
 */
public class UserServiceImpl implements UserService {

    /** 用户持久层 DAO */
    private final UserDao userDao = new UserDaoImpl();

    @Override
    public UserVO login(String uid, String password) {
        if (uid == null || password == null) {
            return null;
        }
        return userDao.login(uid.trim(), password.trim());
    }

    @Override
    public UserVO queryByUid(String uid) {
        if (uid == null) {
            return null;
        }
        return userDao.queryByUid(uid.trim());
    }

    @Override
    public boolean changePassword(String uid, String oldPassword, String newPassword) {
        if (uid == null || oldPassword == null || newPassword == null) {
            return false;
        }
        // 服务端防御性校验：原密码与新密码不能相同
        if (oldPassword.trim().equals(newPassword.trim())) {
            return false;
        }

        UserVO user = userDao.queryByUid(uid.trim());
        if (user == null || !oldPassword.trim().equals(user.getPassword())) {
            return false;
        }

        try {
            return userDao.updatePassword(uid.trim(), newPassword.trim());
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean updateBalance(String uid, BigDecimal newBalance) {
        if (uid == null || newBalance == null || newBalance.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        try {
            return userDao.updateBalance(uid.trim(), newBalance);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}