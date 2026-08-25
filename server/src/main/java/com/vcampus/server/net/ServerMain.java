package com.vcampus.server.net;

/**
 * 服务端启动入口。
 */
public class ServerMain {

    /**
     * 服务端进程入口，直接启动 Socket 监听。
     */
    public static void main(String[] args) {
        new ServerSocketListener().run();
    }
}
