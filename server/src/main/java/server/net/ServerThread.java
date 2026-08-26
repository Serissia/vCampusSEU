package server.net;

import message.Message;
import message.MessageType;
import message.ResponseCode;
import vo.UserVO;
import server.dao.UserDao;
import server.dao.impl.UserDaoImpl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;

/**
 * 该类为一个已连接的客户端分配独立线程，
 * 循环监听并处理客户端发送的序列化 Message 对象
 *
 * @author Serissia
 */
public class ServerThread extends Thread {

    /**
     * 当前客户端的套接字连接
     */
    private final Socket socket;

    /**
     * 对象输出流，向客户端写出数据
     */
    private ObjectOutputStream oos;

    /**
     * 对象输入流，读取客户端传入的数据
     */
    private ObjectInputStream ois;

    /**
     * 线程运行状态控制标志
     */
    private volatile boolean isRunning;

    /**
     * 用户数据访问组件
     */
    private final UserDao userDao;

    public ServerThread(Socket socket) {
        this.socket = socket;
        this.isRunning = true;
        this.userDao = new UserDaoImpl();
    }

    @Override
    public void run() {
        try {
            // 注意：必须先初始化输出流并 flush，防止网络对象流死锁
            oos = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            oos.flush();
            ois = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));

            System.out.println("[服务端] 客户端连接就绪: " + socket.getRemoteSocketAddress());

            while (isRunning) {
                Object obj = ois.readObject();
                if (obj instanceof Message) {
                    Message requestMsg = (Message) obj;
                    System.out.println("[服务端] 收到请求: " + requestMsg.getType() + ", 来源: " + requestMsg.getUid());

                    // 调度并响应请求
                    Message responseMsg = handleMessage(requestMsg);

                    if (responseMsg != null) {
                        oos.writeObject(responseMsg);
                        oos.flush();
                        oos.reset(); // 重置对象输出流，防止缓存导致的对象引用问题
                    }
                }
            }
        } catch (EOFException | SocketException e) {
            System.out.println("[服务端] 客户端正常断开连接: " + socket.getRemoteSocketAddress());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            close();
        }
    }

    /**
     * 业务指令消息分发与调度
     *
     * @param request 请求报文
     * @return 响应报文
     */
    private Message handleMessage(Message request) {
        MessageType type = request.getType();
        Message response = new Message();

        if (type == null) {
            response.setCode(ResponseCode.FAIL);
            return response;
        }

        switch (type) {
            case MSG_LOGIN:
                return handleLogin(request);
            case MSG_LOGOUT:
                this.isRunning = false;
                response.setType(MessageType.MSG_LOGOUT);
                response.setCode(ResponseCode.OK);
                return response;
            default:
                response.setType(type);
                response.setCode(ResponseCode.NOT_FOUND);
                return response;
        }
    }

    /**
     * 处理登录认证逻辑
     *
     * @param request 包含 UserVO 的登录请求
     * @return 登录结果报文
     */
    private Message handleLogin(Message request) {
        Message response = new Message(MessageType.MSG_LOGIN);
        Object data = request.getData();

        if (data instanceof UserVO) {
            UserVO loginParam = (UserVO) data;
            UserVO authenticatedUser = userDao.login(
                    loginParam.getUid(),
                    loginParam.getPassword(),
                    loginParam.getRole()
            );

            if (authenticatedUser != null) {
                // 安全起见，置空返回报文中的敏感密码
                authenticatedUser.setPassword(null);
                response.setCode(ResponseCode.OK);
                response.setData(authenticatedUser);
            } else {
                response.setCode(ResponseCode.UNAUTHORIZED);
            }
        } else {
            response.setCode(ResponseCode.FAIL);
        }

        return response;
    }

    /**
     * 释放通信流与 Socket 资源
     */
    public void close() {
        this.isRunning = false;
        try {
            if (ois != null) {
                ois.close();
            }
            if (oos != null) {
                oos.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}