package vo;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 用户账户实体值对象 (Value Object)
 * @author Serissia
 */
public class UserVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 一卡通号 / 学号 / 教工号 */
    private String uid;
    /** 登录密码 */
    private String password;
    /** 角色: STUDENT, TEACHER, ADMIN */
    private String role;
    /** 真实姓名 */
    private String name;
    /** 一卡通账户余额 */
    private BigDecimal balance;
    /** 状态: 1正常, 0冻结 */
    private Integer status;

    public UserVO() {
    }

    public UserVO(String uid, String password) {
        this.uid = uid;
        this.password = password;
    }

    public UserVO(String uid, String password, String role) {
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
                ", role='" + role + '\'' +
                ", name='" + name + '\'' +
                ", balance=" + balance +
                ", status=" + status +
                '}';
    }
}