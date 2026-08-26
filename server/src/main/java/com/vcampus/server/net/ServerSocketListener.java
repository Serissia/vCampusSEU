package com.vcampus.server.net;

import com.vcampus.server.dispatcher.Dispatcher;

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

    private volatile boolean running = true;

    /**
     * 启动服务端监听，使用固定线程池处理多个客户端连接。
     */
    @Override
    public void run() {
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("vCampusSEU Server 已启动，监听端口 " + PORT);
            while (running) {
                // 每接入一个客户端就交给工作线程处理，主线程继续监听
                Socket socket = serverSocket.accept();
                pool.execute(new ClientHandler(socket, new Dispatcher()));
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
