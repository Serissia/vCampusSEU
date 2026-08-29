package com.vcampus.server.dao;

import com.vcampus.common.vo.UserVO;

import java.math.BigDecimal;
import java.sql.SQLException;

/**
 * 统一用户身份验证 DAO 接口
 *
 * @author Serissia
 */
public interface UserDao {

    /**
     * 用户登录凭证认证
     *
     * @param uid      一卡通账号
     * @param password 密码
     * @return 认证成功返回 UserVO，失败返回 null
     */
    UserVO login(String uid, String password);

    /**
     * 根据 uid 查询用户信息
     *
     * @param uid 用户账号/一卡通号
     * @return UserVO 实体
     */
    UserVO queryByUid(String uid);

    /**
     * 更新用户密码
     *
     * @param uid 用户账号
     * @param newPassword 新密码
     * @return 是否更新成功
     * @throws SQLException 数据库异常
     */
    boolean updatePassword(String uid, String newPassword) throws SQLException;

    /**
     * 更新一卡通账户余额
     *
     * @param uid        用户账号
     * @param newBalance 新余额
     * @return 是否更新成功
     * @throws SQLException 数据库异常
     */
    boolean updateBalance(String uid, BigDecimal newBalance) throws SQLException;
}