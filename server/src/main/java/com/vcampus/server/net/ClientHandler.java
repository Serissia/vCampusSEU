package com.vcampus.server.net;

import com.vcampus.common.message.Message;
import com.vcampus.server.dispatcher.Dispatcher;
import com.vcampus.server.session.SessionManager;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * 单客户端长连接处理线程。
 *
 * @author vCampus Team
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final SessionContext session;
    private final Dispatcher dispatcher;

    /**
     * 构造一条连接的处理线程。
     *
     * <p>会话上下文与分发器都在此处创建，确保「一条连接 ↔ 一个会话 ↔ 一个分发器」严格一一对应：
     * 身份状态不会跨连接串味，也无法从外部注入另一个会话。</p>
     *
     * @param socket         客户端连接
     * @param sessionManager 服务端共享的令牌会话表，用于跨连接认证
     */
    public ClientHandler(Socket socket, SessionManager sessionManager) {
        this.socket = socket;
        this.session = new SessionContext();
        this.dispatcher = new Dispatcher(session, sessionManager);
    }

    /**
     * 维持单个客户端的长连接，循环读取并处理请求。
     */
    @Override
    public void run() {
        ObjectInputStream in = null;
        ObjectOutputStream out = null;
        try {
            // 先创建输出流并 flush，再创建输入流，避免双方对象流握手阻塞
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            // 客户端断开时 readObject 返回 null，循环自然结束
            Object request;
            while ((request = in.readObject()) != null) {
                Message response = dispatcher.dispatch((Message) request);
                out.writeObject(response);
                out.flush();
                out.reset();
            }
        } catch (EOFException | SocketException ignored) {
            // 客户端正常关闭连接或探测断开，无需打印错误堆栈
        } catch (SocketTimeoutException e) {
            // 空闲超时：客户端多半是异常退出（没有 FIN），此处按断线回收连接与线程
            System.out.println("客户端连接空闲超时，已回收：" + socket.getRemoteSocketAddress());
        } catch (IOException e) {
            System.err.println("客户端连接异常：" + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("无法识别客户端报文：" + e.getMessage());
        } finally {
            // 连接断开即注销会话身份，避免已废弃的连接继续持有登录态
            session.clear();
            closeQuietly(in);
            closeQuietly(out);
            closeSocket();
        }
    }

    /**
     * 安全关闭输入流。
     */
    private void closeQuietly(ObjectInputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException ignored) {
                // 忽略关闭异常
            }
        }
    }

    /**
     * 安全关闭输出流。
     */
    private void closeQuietly(ObjectOutputStream out) {
        if (out != null) {
            try {
                out.close();
            } catch (IOException ignored) {
                // 忽略关闭异常
            }
        }
    }

    /**
     * 关闭客户端 Socket。
     */
    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // 忽略关闭异常
        }
    }
}
