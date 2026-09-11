package com.vcampus.server;

import com.vcampus.server.dispatcher.PermissionTable;
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
        // 启动即校验接口权限表的完整性：任何新增接口忘记声明权限都应在此时暴露，而不是变成越权通道
        PermissionTable.verifyComplete();
        System.out.println("[安全] 接口权限表校验通过");
        new ServerSocketListener().run();
    }
}