package com.vcampus.common.vo;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 统一用户档案与一卡通实体
 *
 * @author vCampus Team
 * @version 1.0
 */
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 一卡通号 / 学工号 */
    private String uid;
    /** 登录密码 */
    private String password;
    /** 真实姓名 */
    private String name;
    /** 身份角色 */
    private UserRole role;
    /** 一卡通账户余额 */
    private BigDecimal balance;
    /** 账号状态: 1-正常, 0-冻结 */
    private Integer status;

    public UserVO() {
    }

    public UserVO(String uid, String password) {
        this.uid = uid;
        this.password = password;
    }

    public UserVO(String uid, String password, UserRole role) {
        this.uid = uid;
        this.password = password;
        this.role = role;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    /**
     * 兼容别名获取账号
     *
     * @return 账号
     */
    public String getAccountNumber() {
        return uid;
    }

    public void setAccountNumber(String accountNumber) {
        this.uid = accountNumber;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "UserVO{" +
                "uid='" + uid + '\'' +
                ", password='" + password + '\'' +
                ", name='" + name + '\'' +
                ", role=" + role +
                ", balance=" + balance +
                ", status=" + status +
                '}';
    }
}
