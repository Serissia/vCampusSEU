package com.vcampus.client;

/**
 * 客户端全局启动引导类
 * 用于绕过 JDK 11+ 对非模块化 JavaFX 启动类的限制
 *
 * @author Serissia
 */
public class ClientLauncher {
    public static void main(String[] args) {
        ClientApp.main(args);
    }
}