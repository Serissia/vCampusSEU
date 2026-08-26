package com.vcampus.server.net;

import com.vcampus.common.message.Message;
import com.vcampus.server.dispatcher.Dispatcher;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * 单客户端长连接处理线程。
 *
 * @author vCampus Team
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final Dispatcher dispatcher;

    public ClientHandler(Socket socket, Dispatcher dispatcher) {
        this.socket = socket;
        this.dispatcher = dispatcher;
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
        } catch (IOException e) {
            System.err.println("客户端连接异常：" + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("无法识别客户端报文：" + e.getMessage());
        } finally {
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
