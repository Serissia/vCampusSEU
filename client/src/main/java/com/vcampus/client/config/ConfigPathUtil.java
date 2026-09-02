package com.vcampus.client.config;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 便携路径解析与权限探测工具
 *
 * @author Serissia
 */
public class ConfigPathUtil {

    private static final String CONFIG_FOLDER_NAME = "config";
    private static final String DEFAULT_CONFIG_FILE_NAME = "client-config.json";

    private static File cachedAppDir = null;

    /**
     * 获取应用运行根目录（自动兼容 IDE 与独立打包运行）
     *
     * @return 应用根目录 File
     */
    public static synchronized File getAppDirectory() {
        if (cachedAppDir != null) {
            return cachedAppDir;
        }

        try {
            String path = ConfigPathUtil.class.getProtectionDomain()
                    .getCodeSource().getLocation().getPath();
            String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);
            File file = new File(decodedPath);

            if (file.isFile() || decodedPath.endsWith(".jar")) {
                cachedAppDir = file.getParentFile();
            } else {
                cachedAppDir = file.getParentFile().getParentFile();
            }
        } catch (Exception e) {
            cachedAppDir = new File(".");
        }
        return cachedAppDir;
    }

    /**
     * 获取指定用户的完整配置文件对象
     *
     * @param cardNum 用户一卡通号（为 null 或空时返回默认 client-config.json）
     * @return 配置文件 File
     */
    public static File getConfigFile(String cardNum) {
        File configDir = new File(getAppDirectory(), CONFIG_FOLDER_NAME);
        ensureDirectoryWritable(configDir);

        String fileName = (cardNum == null || cardNum.trim().isEmpty())
                ? DEFAULT_CONFIG_FILE_NAME
                : cardNum.trim() + "-config.json";

        return new File(configDir, fileName);
    }

    /**
     * 校验目录是否具备写入权限
     */
    private static void ensureDirectoryWritable(File dir) {
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created && !dir.exists()) {
                showPermissionAlert(dir.getAbsolutePath());
                return;
            }
        }
        if (!dir.canWrite()) {
            showPermissionAlert(dir.getAbsolutePath());
        }
    }

    /**
     * 弹出目录无写权限告警
     */
    private static void showPermissionAlert(String path) {
        System.err.println("[Config] 运行目录缺少写权限: " + path);
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING,
                    "检测到当前运行目录无写入权限：\n" + path
                            + "\n\n偏好设置将无法保存。请尝试以管理员身份运行或将软件移动到非受限目录。",
                    ButtonType.OK);
            alert.setTitle("权限警告");
            alert.setHeaderText("配置存储受限");
            alert.show();
        });
    }
}