package com.vcampus.common.vo;

import java.io.Serializable;

/**
 * 系统用户值对象。
 */
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String accountNumber;
    private String password;
    private String name;
    private UserRole role;

    /**
     * 无参构造方法，便于序列化框架使用。
     */
    public UserVO() {
    }

    /**
     * 登录请求专用构造方法。
     *
     * @param accountNumber 账号
     * @param password      密码
     */
    public UserVO(String accountNumber, String password) {
        this.accountNumber = accountNumber;
        this.password = password;
    }

    /**
     * 获取账号。
     */
    public String getAccountNumber() {
        return accountNumber;
    }

    /**
     * 设置账号。
     */
    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    /**
     * 获取密码。
     */
    public String getPassword() {
        return password;
    }

    /**
     * 设置密码。
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 获取姓名。
     */
    public String getName() {
        return name;
    }

    /**
     * 设置姓名。
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取用户角色。
     */
    public UserRole getRole() {
        return role;
    }

    /**
     * 设置用户角色。
     */
    public void setRole(UserRole role) {
        this.role = role;
    }
}
