package com.vcampus.client.controller;

import com.vcampus.client.net.SocketClient;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.UserVO;

import java.io.IOException;

/**
 * 登录与用户身份控制器。
 *
 * @author vCampus Team
 */
public class AuthController {

    private final SocketClient socketClient;

    public AuthController(SocketClient socketClient) {
        this.socketClient = socketClient;
    }

    /**
     * 登录：服务端返回 SUCCESS 时提取 UserVO，否则返回 null。
     */
    public UserVO login(String accountNumber, String password) {
        UserVO loginInfo = new UserVO(accountNumber, password);
        Message request = new Message(null, MessageType.LOGIN, null, loginInfo);
        Message response = send(request);
        if (response.getCode() == ResponseCode.SUCCESS && response.getData() instanceof UserVO) {
            return (UserVO) response.getData();
        }
        return null;
    }

    /**
     * 统一发送请求，将网络异常转换为运行时异常。
     */
    private Message send(Message request) {
        try {
            return socketClient.send(request);
        } catch (IOException e) {
            throw new RuntimeException("无法连接服务端", e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("服务端返回数据无法识别", e);
        }
    }
}
