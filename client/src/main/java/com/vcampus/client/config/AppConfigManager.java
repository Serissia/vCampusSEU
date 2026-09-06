package com.vcampus.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.vcampus.client.util.CryptoUtil;


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
            try {
                String rawContent = Files.readString(configFile.toPath(), StandardCharsets.UTF_8).trim();
                String jsonContent = CryptoUtil.isEncrypted(rawContent)
                        ? CryptoUtil.decrypt(rawContent)
                        : rawContent;
                this.currentConfig = GSON.fromJson(jsonContent, AppConfig.class);
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
        try {
            // 核心约束：当未勾选记住密码时，清除密码并置空
            if (!currentConfig.isRememberPassword()) {
                currentConfig.setPassword("");
            }

            String jsonContent = GSON.toJson(getConfig());
            String encryptedContent = CryptoUtil.encrypt(jsonContent);

            Files.writeString(
                    configFile.toPath(),
                    encryptedContent,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            return true;
        } catch (IOException e) {
            System.err.println("[AppConfigManager] 写入配置文件失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取本地所有已登录过的账号配置，并按文件最近修改时间降序排序
     *
     * @return 账号配置列表
     */
    public List<AppConfig> getAllUserConfigs() {
        List<AppConfig> list = new ArrayList<>();
        File dir = new File(ConfigPathUtil.getAppDirectory(), "config");
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith("-config.json") && !"default-config.json".equals(name));
            if (files != null) {
                Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                for (File file : files) {
                    try {
                        String rawContent = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
                        String jsonContent = CryptoUtil.isEncrypted(rawContent)
                                ? CryptoUtil.decrypt(rawContent)
                                : rawContent;
                        AppConfig cfg = GSON.fromJson(jsonContent, AppConfig.class);
                        if (cfg != null && cfg.getCardNum() != null && !cfg.getCardNum().trim().isEmpty()) {
                            list.add(cfg);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return list;
    }

    /**
     * 删除指定账号对应的本地配置文件
     *
     * @param cardNum 一卡通号
     * @return 是否成功删除
     */
    public boolean deleteUserConfig(String cardNum) {
        if (cardNum == null || cardNum.trim().isEmpty()) {
            return false;
        }
        File file = ConfigPathUtil.getConfigFile(cardNum.trim());
        if (file.exists() && file.isFile()) {
            return file.delete();
        }
        return false;
    }
}