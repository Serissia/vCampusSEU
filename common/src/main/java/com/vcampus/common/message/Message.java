package com.vcampus.common.message;

import java.io.Serializable;

/**
 * 前后端统一网络通信报文。
 *
 * @author Serissia
 */
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 请求发起方标识（学号/工号） */
    private String uid;

    /** 业务动作枚举 */
    private MessageType type;

    /** 状态响应码 */
    private ResponseCode code;

    /** 传输负载主体（VO 实体对象、List 集合、Map 容器等） */
    private Object data;

    /**
     * 无参构造方法，便于对象流反序列化时使用。
     */
    public Message() {
    }

    /**
     * 构造一条完整的通信报文。
     *
     * @param uid  请求发起方标识（学号/工号）
     * @param type 业务动作枚举
     * @param code 初始响应码，请求阶段可传 null
     * @param data 业务数据负载
     */
    public Message(String uid, MessageType type, ResponseCode code, Object data) {
        this.uid = uid;
        this.type = type;
        this.code = code;
        this.data = data;
    }

    /**
     * 获取请求发起方标识。
     */
    public String getUid() {
        return uid;
    }

    /**
     * 设置请求发起方标识。
     */
    public void setUid(String uid) {
        this.uid = uid;
    }

    /**
     * 获取业务动作枚举。
     */
    public MessageType getType() {
        return type;
    }

    /**
     * 设置业务动作枚举。
     */
    public void setType(MessageType type) {
        this.type = type;
    }

    /**
     * 获取响应状态码。
     */
    public ResponseCode getCode() {
        return code;
    }

    /**
     * 设置响应状态码。
     */
    public void setCode(ResponseCode code) {
        this.code = code;
    }

    /**
     * 获取数据负载。
     */
    public Object getData() {
        return data;
    }

    /**
     * 设置数据负载。
     */
    public void setData(Object data) {
        this.data = data;
    }
}
