package com.vcampus.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * 客户端偏好设置管理器
 *
 * @author Serissia
 */
public class AppConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile AppConfigManager instance;

    /**
     * 当前活跃的配置实例
     */
    private AppConfig currentConfig;

    /**
     * 当前绑定的卡号（为空表示未登录状态）
     */
    private String currentCardNum = "";

    private AppConfigManager() {
        // 初始化时加载默认/未登录配置
        switchUser(null);
    }

    /**
     * 获取管理器单例
     *
     * @return AppConfigManager 实例
     */
    public static AppConfigManager getInstance() {
        if (instance == null) {
            synchronized (AppConfigManager.class) {
                if (instance == null) {
                    instance = new AppConfigManager();
                }
            }
        }
        return instance;
    }

    /**
     * 获取当前生效的配置对象
     *
     * @return AppConfig 实例
     */
    public AppConfig getConfig() {
        if (currentConfig == null) {
            currentConfig = new AppConfig();
        }
        return currentConfig;
    }

    /**
     * 切换用户配置上下文（登录成功或退出时调用）
     *
     * @param cardNum 用户一卡通号，传入 null 或空则切换为默认未登录配置
     */
    public synchronized void switchUser(String cardNum) {
        this.currentCardNum = (cardNum == null) ? "" : cardNum.trim();
        File configFile = ConfigPathUtil.getConfigFile(this.currentCardNum);

        boolean parseFailed = false;

        if (configFile.exists() && configFile.isFile()) {
            try (FileReader reader = new FileReader(configFile, StandardCharsets.UTF_8)) {
                this.currentConfig = GSON.fromJson(reader, AppConfig.class);
                if (this.currentConfig != null) {
                    return;
                }
            } catch (Exception e) {
                parseFailed = true;
                backupCorruptedConfig(configFile);
                System.err.println("[AppConfigManager] 读取配置文件失败，已保留原文件并禁用自动覆盖: " + e.getMessage());
            }
        }

        // 文件不存在时创建新配置并持久化；解析失败时禁止自动覆盖原文件
        this.currentConfig = new AppConfig();
        this.currentConfig.setCardNum(this.currentCardNum);
        if (!this.currentCardNum.isEmpty()) {
            this.currentConfig.setLastCardNum(this.currentCardNum);
        }
        if (!parseFailed) {
            saveConfig();
        }
    }

    private void backupCorruptedConfig(File configFile) {
        File backupFile = new File(configFile.getAbsolutePath() + ".bak");
        try {
            Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.err.println("[AppConfigManager] 已备份损坏配置文件: " + backupFile.getAbsolutePath());
        } catch (IOException backupEx) {
            System.err.println("[AppConfigManager] 备份损坏配置文件失败: " + backupEx.getMessage());
        }
    }

    /**
     * 退出登录时重置为默认配置
     */
    public synchronized void resetToDefault() {
        switchUser(null);
    }

    /**
     * 将当前配置持久化写入本地 JSON
     *
     * @return 是否保存成功
     */
    public synchronized boolean saveConfig() {
        File configFile = ConfigPathUtil.getConfigFile(this.currentCardNum);
        try (FileWriter writer = new FileWriter(configFile, StandardCharsets.UTF_8)) {
            GSON.toJson(getConfig(), writer);
            return true;
        } catch (IOException e) {
            System.err.println("[AppConfigManager] 写入配置文件失败: " + e.getMessage());
            return false;
        }
    }
}