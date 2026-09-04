package com.vcampus.server.service.impl;

import com.vcampus.common.vo.UserVO;
import com.vcampus.server.dao.UserDao;
import com.vcampus.server.dao.impl.UserDaoImpl;
import com.vcampus.server.service.UserService;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

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
        // 直接按 uid 查询（不过滤状态），登录时由上层判断账号是否被冻结
        UserVO user = userDao.queryByUid(uid.trim());
        if (user == null) {
            return null;
        }
        if (!password.trim().equals(user.getPassword())) {
            return null;
        }
        return user;
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

    @Override
    public boolean createUser(UserVO user) {
        if (user == null || user.getUid() == null || user.getUid().trim().isEmpty()
                || user.getName() == null || user.getName().trim().isEmpty()
                || user.getPassword() == null || user.getPassword().trim().isEmpty()
                || user.getRole() == null) {
            return false;
        }
        try {
            return userDao.createUser(user);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<UserVO> listAllUsers() {
        try {
            return userDao.listAllUsers();
        } catch (SQLException e) {
            throw new RuntimeException("查询用户列表失败", e);
        }
    }

    @Override
    public boolean uidExists(String uid) {
        if (uid == null || uid.trim().isEmpty()) {
            return false;
        }
        try {
            return userDao.uidExists(uid.trim());
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean updateUserInfo(String oldUid, String newUid, String name, String role, String status) {
        if (oldUid == null || newUid == null || name == null || role == null || status == null) {
            return false;
        }
        try {
            return userDao.updateUserInfo(oldUid.trim(), newUid.trim(), name.trim(), role.trim(), status.trim());
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean resetPassword(String uid, String newPassword) {
        if (uid == null || newPassword == null || newPassword.trim().length() < 6) {
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
    public boolean deleteUser(String uid) {
        if (uid == null || uid.trim().isEmpty()) {
            return false;
        }
        try {
            return userDao.deleteUser(uid.trim());
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}