package com.vcampus.server.service;

import com.vcampus.common.vo.UserVO;

/**
 * 用户登录业务接口。
 */
public interface UserService {

    /**
     * 根据账号和密码完成登录鉴权。
     */
    UserVO login(String accountNumber, String password);
}
