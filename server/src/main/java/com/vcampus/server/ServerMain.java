package com.vcampus.server;

import com.vcampus.server.net.ServerThread;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 服务端主启动入口
 *
 * @author Serissia
 */
public class ServerMain {

    /** 服务端监听网络端口号 */
    private static final int SERVER_PORT = 8888;

    /** 服务端运行标志 */
    private static volatile boolean running = true;

    public static void main(String[] args) {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(SERVER_PORT);
            System.out.println("=================================================");
            System.out.println("  虚拟校园系统 (vCampusSEU) 服务端已成功启动");
            System.out.println("  监听本地端口: " + SERVER_PORT);
            System.out.println("=================================================");

            while (running) {
                Socket socket = serverSocket.accept();
                System.out.println("[服务端] 捕获新连接，正在分配工作线程: " + socket.getRemoteSocketAddress());
                new ServerThread(socket).start();
            }
        } catch (IOException e) {
            System.err.println("[服务端] 端口监听异常: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (serverSocket != null && !serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}