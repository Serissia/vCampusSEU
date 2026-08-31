package com.vcampus.client.controller;

import com.vcampus.client.config.AppConfig;
import com.vcampus.client.config.AppConfigManager;
import com.vcampus.client.config.ConfigPathUtil;
import com.vcampus.client.util.ScrollSpeedUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.awt.Desktop;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 客户端偏好设置控制器
 *
 * @author Serissia
 */
public class SettingsController {

    /**
     * 设置页后台线程池，统一命名并复用线程，避免每次点击创建裸线程。
     */
    private static final ExecutorService THREAD_POOL = new ThreadPoolExecutor(
            1,
            2,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(20),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "Settings-Thread-" + threadNumber.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private final AtomicBoolean connectionCheckRunning = new AtomicBoolean(false);

    @FXML
    private ScrollPane settingsScrollPane;

    // --- 主题与外观 ---

    @FXML
    private ToggleGroup themeToggleGroup;
    @FXML
    private RadioButton themeLightRadio;
    @FXML
    private RadioButton themeDarkRadio;
    @FXML
    private RadioButton themeSystemRadio;
    @FXML
    private HBox colorPaletteGroup;

    // --- 网络通信 ---

    @FXML
    private TextField serverHostField;
    @FXML
    private TextField serverPortField;
    @FXML
    private TextField connectTimeoutField;
    @FXML
    private ProgressIndicator testingSpinner;
    @FXML
    private Label testResultLabel;

    // --- 交互与行为 ---

    @FXML
    private Slider scrollSpeedSlider;
    @FXML
    private Label scrollSpeedValueLabel;
    @FXML
    private ComboBox<String> closeBehaviorCombo;
    @FXML
    private CheckBox rememberCardNumCheck;

    // --- 本地存储 ---

    @FXML
    private TextField configFilePathField;
    @FXML
    private Label statusMessageLabel;

    /**
     * 预设色彩：东南绿、飞书蓝、活力橙、优雅紫、经典黑
     */
    private static final Map<String, String> PRESET_COLORS = new LinkedHashMap<>();

    static {
        PRESET_COLORS.put("东南绿", "#487A32");
        PRESET_COLORS.put("飞书蓝", "#3370FF");
        PRESET_COLORS.put("活力橙", "#FF7D00");
        PRESET_COLORS.put("优雅紫", "#722ED1");
        PRESET_COLORS.put("商务深灰", "#333333");
    }

    private String selectedAccentColor = "#487A32";

    @FXML
    public void initialize() {
        // 绑定平滑滚动
        if (settingsScrollPane != null) {
            ScrollSpeedUtil.applyCustomScrollSpeed(settingsScrollPane);
        }

        initCloseBehaviorOptions();
        initColorPalette();
        initSliderListener();
        loadCurrentConfigToUI();
    }

    /**
     * 初始化关闭窗口下拉选项
     */
    private void initCloseBehaviorOptions() {
        closeBehaviorCombo.setItems(FXCollections.observableArrayList(
                "直接退出程序 (Exit)",
                "最小化到托盘/任务栏 (Minimize)"
        ));
    }

    /**
     * 初始化预设颜色圆圈调色板
     */
    private void initColorPalette() {
        colorPaletteGroup.getChildren().clear();
        for (Map.Entry<String, String> entry : PRESET_COLORS.entrySet()) {
            String hex = entry.getValue();
            Region circle = new Region();
            circle.getStyleClass().add("color-circle");
            circle.setStyle("-fx-background-color: " + hex + ";");

            circle.setOnMouseClicked(event -> {
                selectedAccentColor = hex;
                updatePaletteSelection();
            });
            colorPaletteGroup.getChildren().add(circle);
        }
    }

    /**
     * 更新调色板高亮状态
     */
    private void updatePaletteSelection() {
        for (int i = 0; i < colorPaletteGroup.getChildren().size(); i++) {
            Region circle = (Region) colorPaletteGroup.getChildren().get(i);
            String hex = (String) PRESET_COLORS.values().toArray()[i];
            circle.getStyleClass().remove("selected");
            if (hex.equalsIgnoreCase(selectedAccentColor)) {
                circle.getStyleClass().add("selected");
            }
        }
    }

    /**
     * 绑定滚动倍率 Slider 显示
     */
    private void initSliderListener() {
        scrollSpeedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double formatted = Math.round(newVal.doubleValue() * 10.0) / 10.0;
            scrollSpeedValueLabel.setText(formatted + "x");
        });
    }

    /**
     * 从配置中心加载数据渲染到 UI 控件
     */
    public void loadCurrentConfigToUI() {
        AppConfig config = AppConfigManager.getInstance().getConfig();

        // 外观
        String themeMode = config.getThemeMode();
        if ("dark".equalsIgnoreCase(themeMode)) {
            themeDarkRadio.setSelected(true);
        } else if ("system".equalsIgnoreCase(themeMode)) {
            themeSystemRadio.setSelected(true);
        } else {
            themeLightRadio.setSelected(true);
        }
        selectedAccentColor = config.getAccentColor();
        updatePaletteSelection();

        // 网络
        serverHostField.setText(config.getServerHost());
        serverPortField.setText(String.valueOf(config.getServerPort()));
        connectTimeoutField.setText(String.valueOf(config.getConnectTimeoutMs()));

        // 交互
        scrollSpeedSlider.setValue(config.getScrollSpeedFactor());
        scrollSpeedValueLabel.setText(config.getScrollSpeedFactor() + "x");
        if ("minimize".equalsIgnoreCase(config.getCloseBehavior())) {
            closeBehaviorCombo.getSelectionModel().select(1);
        } else {
            closeBehaviorCombo.getSelectionModel().select(0);
        }
        rememberCardNumCheck.setSelected(config.isRememberCardNum());

        // 存储路径
        File currentFile = ConfigPathUtil.getConfigFile(config.getCardNum());
        configFilePathField.setText(currentFile.getAbsolutePath());
    }

    /**
     * 保存偏好设置并即时应用生效
     */
    @FXML
    private void handleSaveSettings() {
        try {
            String host = serverHostField.getText().trim();
            int port = Integer.parseInt(serverPortField.getText().trim());
            int timeout = Integer.parseInt(connectTimeoutField.getText().trim());

            if (host.isEmpty() || port <= 0 || port > 65535 || timeout < 500) {
                showAlert("输入错误", "请输入有效的 IP 地址、端口(1-65535)及超时时间(>=500ms)", Alert.AlertType.WARNING);
                return;
            }

            AppConfig config = AppConfigManager.getInstance().getConfig();
            config.setServerHost(host);
            config.setServerPort(port);
            config.setConnectTimeoutMs(timeout);
            if (themeDarkRadio.isSelected()) {
                config.setThemeMode("dark");
            } else if (themeSystemRadio.isSelected()) {
                config.setThemeMode("system");
            } else {
                config.setThemeMode("light");
            }
            config.setAccentColor(selectedAccentColor);

            double speedFactor = Math.round(scrollSpeedSlider.getValue() * 10.0) / 10.0;
            config.setScrollSpeedFactor(speedFactor);
            config.setCloseBehavior(closeBehaviorCombo.getSelectionModel().getSelectedIndex() == 1 ? "minimize" : "exit");
            config.setRememberCardNum(rememberCardNumCheck.isSelected());

            // 1. 持久化到 JSON 文件
            boolean ok = AppConfigManager.getInstance().saveConfig();

            // 2. 动态更新内存中运行时的全局变量
            ScrollSpeedUtil.SPEED_MULTIPLIER.set(speedFactor);

            if (ok) {
                showStatusMessage("偏好设置已成功保存并立即生效！");
            } else {
                showAlert("保存受限", "保存失败，请检查运行目录写权限。", Alert.AlertType.ERROR);
            }
        } catch (NumberFormatException e) {
            showAlert("格式错误", "端口号与超时时间必须为有效整数！", Alert.AlertType.ERROR);
        }
    }

    /**
     * 测试网络连通性 (后台非阻塞探测)
     */
    @FXML
    private void handleTestConnection() {
        if (!connectionCheckRunning.compareAndSet(false, true)) {
            return;
        }

        String host = serverHostField.getText().trim();
        int port;
        int timeout;
        try {
            port = Integer.parseInt(serverPortField.getText().trim());
            timeout = Integer.parseInt(connectTimeoutField.getText().trim());
        } catch (Exception e) {
            connectionCheckRunning.set(false);
            setTestResult("端口或超时格式错误", false);
            return;
        }

        testingSpinner.setVisible(true);
        testResultLabel.setText("正在探测连接...");
        testResultLabel.getStyleClass().removeAll("success", "fail");

        THREAD_POOL.execute(() -> {
            boolean reachable = false;
            long start = System.currentTimeMillis();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), Math.min(timeout, 3000));
                reachable = true;
            } catch (Exception ignored) {
            }
            long cost = System.currentTimeMillis() - start;

            boolean finalReachable = reachable;
            Platform.runLater(() -> {
                try {
                    testingSpinner.setVisible(false);
                    if (finalReachable) {
                        setTestResult("连接成功 (延迟 " + cost + "ms)", true);
                    } else {
                        setTestResult("无法连接到服务器，请检查服务是否开启", false);
                    }
                } finally {
                    connectionCheckRunning.set(false);
                }
            });
        });
    }

    private void setTestResult(String msg, boolean success) {
        testResultLabel.setText(msg);
        testResultLabel.getStyleClass().removeAll("success", "fail");
        testResultLabel.getStyleClass().add(success ? "success" : "fail");
    }

    /**
     * 恢复默认配置
     */
    @FXML
    private void handleResetDefaults() {
        AppConfig config = AppConfigManager.getInstance().getConfig();
        String currentCard = config.getCardNum();

        AppConfig defaultConfig = new AppConfig();
        defaultConfig.setCardNum(currentCard);

        // 重新赋值回配置中心
        config.setServerHost(defaultConfig.getServerHost());
        config.setServerPort(defaultConfig.getServerPort());
        config.setConnectTimeoutMs(defaultConfig.getConnectTimeoutMs());
        config.setThemeMode(defaultConfig.getThemeMode());
        config.setAccentColor(defaultConfig.getAccentColor());
        config.setScrollSpeedFactor(defaultConfig.getScrollSpeedFactor());
        config.setCloseBehavior(defaultConfig.getCloseBehavior());
        config.setRememberCardNum(defaultConfig.isRememberCardNum());

        AppConfigManager.getInstance().saveConfig();
        ScrollSpeedUtil.SPEED_MULTIPLIER.set(defaultConfig.getScrollSpeedFactor());
        loadCurrentConfigToUI();

        showStatusMessage("已恢复为系统默认配置！");
    }

    /**
     * 打开配置所在目录
     */
    @FXML
    private void handleOpenConfigDir() {
        try {
            File dir = new File(ConfigPathUtil.getAppDirectory(), "config");
            if (!dir.exists()) {
                if(!dir.mkdirs()) {
                    showAlert("打开失败", "无法创建配置目录: " + dir.getAbsolutePath(), Alert.AlertType.ERROR);
                    return;
                }
            }
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir);
            } else {
                showAlert("提示", "当前系统环境不支持自动打开文件夹，路径为: " + dir.getAbsolutePath(), Alert.AlertType.INFORMATION);
            }
        } catch (Exception e) {
            showAlert("打开失败", "无法打开目录: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    /**
     * 重新从本地磁盘读取
     */
    @FXML
    private void handleReloadFromDisk() {
        AppConfig config = AppConfigManager.getInstance().getConfig();
        AppConfigManager.getInstance().switchUser(config.getCardNum());
        loadCurrentConfigToUI();
        showStatusMessage("已重新自本地磁盘载入配置！");
    }

    private void showStatusMessage(String msg) {
        statusMessageLabel.setText(msg);
        statusMessageLabel.setVisible(true);
        statusMessageLabel.setManaged(true);
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}