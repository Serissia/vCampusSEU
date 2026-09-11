package com.vcampus.server.net;

import com.vcampus.server.session.SessionManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 服务端端口监听与工作线程池维护。
 *
 * @author vCampus Team
 */
public class ServerSocketListener implements Runnable {

    public static final int PORT = 8888;
    private static final int THREAD_COUNT = 20;

    /**
     * 连接空闲超时（毫秒）。客户端每 60 秒发一次心跳，这里给三次的余量。
     *
     * <p>作用：客户端异常退出（拔网线、断电、被强杀）时不会有 TCP FIN 通知服务端，
     * 处理线程会永久阻塞在 readObject 上，连同会话一起占住固定线程池的一个名额；
     * 累计到线程池上限后服务端就再也接不进新客户端。设置读超时后，这类静默死连接
     * 会在几分钟内被判为超时，走正常的断开清理流程。</p>
     */
    private static final int IDLE_TIMEOUT_MS = 180_000;

    private volatile boolean running = true;

    /**
     * 启动服务端监听，使用固定线程池处理多个客户端连接。
     */
    @Override
    public void run() {
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        // 令牌会话表由全部连接共享：这是身份能够脱离单条连接的前提
        SessionManager sessionManager = new SessionManager();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("vCampusSEU Server 已启动，监听端口 " + PORT);
            while (running) {
                // 每接入一个客户端就交给工作线程处理，主线程继续监听
                Socket socket = serverSocket.accept();
                // 读超时只作用于「等待客户端下一个请求」这一动作，不影响正在处理中的请求耗时
                socket.setSoTimeout(IDLE_TIMEOUT_MS);
                // 会话上下文与分发器由 ClientHandler 内部按连接创建，外部不再手工组装
                pool.execute(new ClientHandler(socket, sessionManager));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            pool.shutdown();
        }
    }

    /**
     * 通知监听循环停止运行。
     */
    public void stop() {
        running = false;
    }
}
