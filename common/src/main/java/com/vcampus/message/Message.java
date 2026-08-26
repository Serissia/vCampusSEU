package com.vcampus.message;

import java.io.Serializable;

/**
 * 统一网络通信报文对象
 * 必须实现 Serializable 接口，保证网络对象流正常收发
 * @author Serissia
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 发起请求的用户标识 (学号/工号) */
    private String uid;
    /** 业务动作类型 */
    private MessageType type;
    /** 状态响应码 (服务端返回时使用) */
    private ResponseCode code;
    /** 传输的数据载荷 (VO、List、String 等) */
    private Object data;

    public Message() {
    }

    public Message(MessageType type) {
        this.type = type;
    }

    public Message(MessageType type, Object data) {
        this.type = type;
        this.data = data;
    }

    public Message(String uid, MessageType type, Object data) {
        this.uid = uid;
        this.type = type;
        this.data = data;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public ResponseCode getCode() {
        return code;
    }

    public void setCode(ResponseCode code) {
        this.code = code;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "Message{" +
                "uid='" + uid + '\'' +
                ", type=" + type +
                ", code=" + code +
                ", data=" + data +
                '}';
    }
}