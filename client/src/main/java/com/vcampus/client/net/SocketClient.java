package com.vcampus.client.net;

import com.vcampus.common.message.Message;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Properties;

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
     * 使用默认的 client.properties 构造客户端。
     */
    public SocketClient() {
        Properties props = new Properties();
        try (InputStream stream = SocketClient.class.getClassLoader()
                .getResourceAsStream("client.properties")) {
            if (stream != null) {
                props.load(stream);
            }
        } catch (IOException e) {
            throw new RuntimeException("读取客户端配置失败", e);
        }
        this.host = props.getProperty("server.host", "127.0.0.1");
        this.port = Integer.parseInt(props.getProperty("server.port", "8888"));
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
        socket = new Socket(host, port);
        // 与服务端保持相同的对象流初始化顺序
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
    }

    /**
     * 发送请求并同步等待服务端响应。
     */
    public synchronized Message send(Message request) throws IOException, ClassNotFoundException {
        connect();
        out.writeObject(request);
        out.flush();
        out.reset();
        return (Message) in.readObject();
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