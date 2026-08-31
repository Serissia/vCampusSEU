package com.vcampus.client.config;

import java.io.Serial;
import java.io.Serializable;

/**
 * 客户端偏好设置实体类
 *
 * @author Serissia
 */
public class AppConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 绑定的卡号/用户唯一标识
     */
    private String cardNum = "";

    /**
     * 服务器主机地址
     */
    private String serverHost = "127.0.0.1";

    /**
     * 服务器端口号
     */
    private int serverPort = 8888;

    /**
     * 网络连接超时时间（毫秒）
     */
    private int connectTimeoutMs = 5000;

    /**
     * 主题模式: light / dark / system
     */
    private String themeMode = "light";

    /**
     * 主题强调色 Hex 值
     */
    private String accentColor = "#3370ff";

    /**
     * 界面平滑滚动倍率
     */
    private double scrollSpeedFactor = 2.5;

    /**
     * 关闭窗口行为: exit / minimize
     */
    private String closeBehavior = "exit";

    /**
     * 是否记住卡号
     */
    private boolean rememberCardNum = true;

    /**
     * 上次成功登录的卡号
     */
    private String lastCardNum = "";

    public AppConfig() {
    }

    public String getCardNum() {
        return cardNum;
    }

    public void setCardNum(String cardNum) {
        this.cardNum = cardNum;
    }

    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public String getThemeMode() {
        return themeMode;
    }

    public void setThemeMode(String themeMode) {
        this.themeMode = themeMode;
    }

    public String getAccentColor() {
        return accentColor;
    }

    public void setAccentColor(String accentColor) {
        this.accentColor = accentColor;
    }

    public double getScrollSpeedFactor() {
        return scrollSpeedFactor;
    }

    public void setScrollSpeedFactor(double scrollSpeedFactor) {
        this.scrollSpeedFactor = scrollSpeedFactor;
    }

    public String getCloseBehavior() {
        return closeBehavior;
    }

    public void setCloseBehavior(String closeBehavior) {
        this.closeBehavior = closeBehavior;
    }

    public boolean isRememberCardNum() {
        return rememberCardNum;
    }

    public void setRememberCardNum(boolean rememberCardNum) {
        this.rememberCardNum = rememberCardNum;
    }

    public String getLastCardNum() {
        return lastCardNum;
    }

    public void setLastCardNum(String lastCardNum) {
        this.lastCardNum = lastCardNum;
    }
}