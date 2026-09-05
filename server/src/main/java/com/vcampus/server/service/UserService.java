package com.vcampus.server.service;

import com.vcampus.common.vo.UserVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户业务接口
 *
 * @author Serissia
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

    /**
     * 修改用户密码（服务端校验原密码与新密码差异）
     *
     * @param uid         用户账号
     * @param oldPassword 原密码
     * @param newPassword 新密码
     * @return 是否修改成功
     */
    boolean changePassword(String uid, String oldPassword, String newPassword);

    /**
     * 更新一卡通账户余额（充值/扣费）
     *
     * @param uid        用户账号
     * @param newBalance 新余额
     * @return 是否更新成功
     */
    boolean updateBalance(String uid, BigDecimal newBalance);

    /**
     * 注册新用户（初始余额为 0，状态正常）。
     */
    boolean createUser(UserVO user);

    /**
     * 列出所有用户。
     */
    List<UserVO> listAllUsers();

    /**
     * 判断账号是否已存在。
     */
    boolean uidExists(String uid);

    /**
     * 修改用户账号、姓名、角色、状态（不修改余额与密码）。
     */
    boolean updateUserInfo(String oldUid, String newUid, String name, String role, String status);

    /**
     * 管理员重置用户密码（不校验原密码）。
     */
    boolean resetPassword(String uid, String newPassword);

    /**
     * 删除用户。
     */
    boolean deleteUser(String uid);
}