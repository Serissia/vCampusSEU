package com.vcampus.server;

import com.vcampus.server.net.ServerSocketListener;

/**
 * 服务端主程序启动入口
 *
 * @author vCampus Team
 */
public class ServerMain {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  虚拟校园系统 (vCampusSEU) 服务端正在启动...");
        System.out.println("=================================================");
        new ServerSocketListener().run();
    }
}