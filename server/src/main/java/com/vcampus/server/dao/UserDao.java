package com.vcampus.server.dao;

import com.vcampus.vo.UserVO;

/**
 * 用户数据访问接口
 *
 * @author Serissia
 */
public interface UserDao {

    /**
     * 根据学号/工号及密码、角色进行登录凭证校验
     *
     * @param uid      用户账号
     * @param password 登录密码
     * @param role     用户角色
     * @return 校验成功返回完整 UserVO，账号密码不匹配返回 null
     */
    UserVO login(String uid, String password, String role);

    /**
     * 根据学号/工号查询用户基本信息
     *
     * @param uid 用户账号
     * @return 用户基本信息实体
     */
    UserVO queryByUid(String uid);
}