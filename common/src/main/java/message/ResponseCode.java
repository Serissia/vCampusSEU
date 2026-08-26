package message;

import java.io.Serializable;

/**
 * 通信响应状态码枚举
 * @author Serissia
 */
public enum ResponseCode implements Serializable {
    OK(200, "操作成功"),
    FAIL(400, "操作失败 / 业务异常"),
    UNAUTHORIZED(401, "身份未认证 / 用户名或密码错误"),
    FORBIDDEN(403, "权限不足"),
    NOT_FOUND(404, "数据或资源未找到"),
    SERVER_ERROR(500, "服务端内部处理异常");

    private final int code;
    private final String message;

    ResponseCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}