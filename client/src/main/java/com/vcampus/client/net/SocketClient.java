package com.vcampus.client.net;

import com.vcampus.client.config.AppConfig;
import com.vcampus.client.config.AppConfigManager;
import com.vcampus.common.message.Message;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 客户端 Socket 连接与对象流收发封装。
 *
 * @author vCampus Team
 */
public class SocketClient implements Closeable {

    private final String host;
    private final int port;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    /**
     * 登录令牌，由 {@link ClientSession} 在登录/登出时维护。非空时随每个请求一起发出，
     * 使这条连接在服务端看来具备身份——换连接重连后也能凭它直接恢复登录态，无需重新输密码。
     */
    private volatile String sessionToken;

    /**
     * 使用 AppConfigManager 中的服务端地址和端口构造客户端。
     */
    public SocketClient() {
        AppConfig config = AppConfigManager.getInstance().getConfig();
        this.host = config.getServerHost();
        this.port = config.getServerPort();
    }

    /**
     * 使用指定的服务端地址和端口构造客户端。
     */
    public SocketClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * 建立到服务端的长连接，并初始化对象流。
     */
    public synchronized void connect() throws IOException {
        if (socket != null && !socket.isClosed()) {
            return;
        }
        socket = new Socket();
        // 使用配置里的连接超时，避免服务端不可达时后台线程被系统默认超时挂住
        int timeout = AppConfigManager.getInstance().getConfig().getConnectTimeoutMs();
        socket.connect(new InetSocketAddress(host, port), timeout);
        // 与服务端保持相同的对象流初始化顺序
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
    }

    /**
     * 设置本连接使用的登录令牌，传 null 表示清除。
     */
    void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    /**
     * 发送请求并同步等待服务端响应。
     *
     * <p>连接若在发送阶段失效（服务端重启、网络抖动、连接被对端关闭），会重连一次再重发：
     * 写失败意味着请求<b>尚未</b>抵达服务端，重发不会造成重复执行。相反，如果是在读响应阶段
     * 失败，请求可能已经被处理过，此时只关闭连接、原样抛出异常，交由调用方自行重试，
     * 以免把一次借书、一次下单变成两次。</p>
     */
    public synchronized Message send(Message request) throws IOException, ClassNotFoundException {
        if (sessionToken != null) {
            request.setToken(sessionToken);
        }

        connect();
        try {
            out.writeObject(request);
            out.flush();
            out.reset();
        } catch (IOException writeFailure) {
            // 请求未送出，可以安全地重连后重试一次。
            // 这里不能加「只有带令牌才重试」的限制：登录请求本身就没有令牌，
            // 而登录页停留超过服务端空闲超时后连接会被回收，那条登录请求同样需要重连。
            close();
            connect();
            out.writeObject(request);
            out.flush();
            out.reset();
        }

        try {
            return (Message) in.readObject();
        } catch (IOException readFailure) {
            // 请求可能已被处理，不重试；关闭连接让下一次调用重新建立并凭令牌恢复身份
            close();
            throw readFailure;
        }
    }

    /**
     * 关闭连接并释放对象流与 Socket 资源。
     */
    @Override
    public synchronized void close() {
        try {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // 忽略关闭异常
        } finally {
            in = null;
            out = null;
            socket = null;
        }
    }
}