package com.vcampus.server.service;

import com.vcampus.common.vo.UserVO;

/**
 * 统一用户身份验证业务接口
 *
 * @author Serissia
 * @version 1.0
 */
public interface UserService {

    /**
     * 用户登录凭证认证
     *
     * @param uid      一卡通账号
     * @param password 密码
     * @return 认证成功返回 UserVO，失败返回 null
     */
    UserVO login(String uid, String password);

    /**
     * 查询指定用户信息
     *
     * @param uid 用户账号
     * @return 用户基本信息
     */
    UserVO queryByUid(String uid);
}
