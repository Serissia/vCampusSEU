package com.vcampus.server.service.impl;

import com.vcampus.common.vo.UserVO;
import com.vcampus.server.dao.UserDao;
import com.vcampus.server.dao.impl.UserDaoImpl;
import com.vcampus.server.service.UserService;

/**
 * 用户业务接口实现
 *
 * @author Serissia
 * @version 1.0
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
}
