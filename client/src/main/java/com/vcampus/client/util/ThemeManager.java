package com.vcampus.client.util;

import com.vcampus.client.config.AppConfig;
import com.vcampus.client.config.AppConfigManager;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * 客户端全局主题与壁纸渲染管理器
 *
 * @author Serissia
 */
public final class ThemeManager {

    private ThemeManager() {
    }

    public static void applyTheme(Scene scene) {
        if (scene == null || scene.getRoot() == null) {
            return;
        }

        AppConfig config = AppConfigManager.getInstance().getConfig();
        Parent root = scene.getRoot();

        String primary = config.getAccentColor();
        String primaryHover = MonetColorUtil.getHoverColor(primary);
        String primaryPressed = MonetColorUtil.getPressedColor(primary);
        String primaryLight = MonetColorUtil.getLightContainerColor(primary);

        boolean isDark = false;
        if ("dark".equalsIgnoreCase(config.getThemeMode())) {
            isDark = true;
        } else if ("system".equalsIgnoreCase(config.getThemeMode())) {
            isDark = isSystemInDarkMode();
        }

        StringBuilder cssVars = new StringBuilder();
        cssVars.append("-fx-primary: ").append(primary).append(";");
        cssVars.append("-fx-primary-hover: ").append(primaryHover).append(";");
        cssVars.append("-fx-primary-pressed: ").append(primaryPressed).append(";");
        cssVars.append("-fx-primary-light: ").append(primaryLight).append(";");

        if (isDark) {
            cssVars.append("-fx-bg-base: #1E1F22;");
            cssVars.append("-fx-bg-surface: #2B2D30;");
            cssVars.append("-fx-bg-nav: #26282B;");
            cssVars.append("-fx-text-main: #DFE1E5;");
            cssVars.append("-fx-text-muted: #9DA0A8;");
            cssVars.append("-fx-text-disabled: #6F737A;");
            cssVars.append("-fx-border-base: #393B40;");
        } else {
            cssVars.append("-fx-bg-base: #F7F8FA;");
            cssVars.append("-fx-bg-surface: #FFFFFF;");
            cssVars.append("-fx-bg-nav: #F2F3F5;");
            cssVars.append("-fx-text-main: #1F2329;");
            cssVars.append("-fx-text-muted: #646A73;");
            cssVars.append("-fx-text-disabled: #8F959E;");
            cssVars.append("-fx-border-base: #DEE0E3;");
        }

        root.setStyle(cssVars.toString());

        applyWallpaper(scene, config, isDark);
    }

    public static boolean isSystemInDarkMode() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{
                        "reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "/v", "AppsUseLightTheme"
                });
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("AppsUseLightTheme")) {
                            return line.contains("0x0");
                        }
                    }
                }
            } catch (Exception ignored) { }
        } else if (os.contains("mac")) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{
                        "defaults", "read", "-g", "AppleInterfaceStyle"
                });
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null && "Dark".equalsIgnoreCase(line.trim())) {
                        return true;
                    }
                }
            } catch (Exception ignored) { }
        }
        return false;
    }

    private static void applyWallpaper(Scene scene, AppConfig config, boolean isDark) {
        ImageView bgImageView = (ImageView) scene.lookup("#bgWallpaperImageView");
        Region scrimOverlay = (Region) scene.lookup("#bgScrimOverlay");
        StackPane rootStack = (StackPane) scene.lookup("#mainRootStackPane");

        if (bgImageView == null || scrimOverlay == null) {
            return;
        }

        if (rootStack != null) {
            bgImageView.fitWidthProperty().bind(rootStack.widthProperty());
            bgImageView.fitHeightProperty().bind(rootStack.heightProperty());
            scrimOverlay.prefWidthProperty().bind(rootStack.widthProperty());
            scrimOverlay.prefHeightProperty().bind(rootStack.heightProperty());
        }

        String bgPath = config.getCustomBgPath();
        if (bgPath != null && !bgPath.trim().isEmpty()) {
            File bgFile = new File(bgPath);
            if (bgFile.exists() && bgFile.isFile()) {
                try {
                    // 解决含有反斜杠或空格的绝对路径加载问题
                    Image img = new Image(bgFile.toURI().toString());
                    bgImageView.setImage(img);
                    bgImageView.setVisible(true);

                    scrimOverlay.setVisible(true);
                    scrimOverlay.setOpacity(config.getBgScrimOpacity());
                    scrimOverlay.setStyle("-fx-background-color: " + (isDark ? "#121212" : "#FFFFFF") + ";");
                    return;
                } catch (Exception e) {
                    System.err.println("[ThemeManager] 壁纸加载失败: " + e.getMessage());
                }
            }
        }

        bgImageView.setImage(null);
        bgImageView.setVisible(false);
        scrimOverlay.setVisible(false);
    }
}