package com.vcampus.client.controller;

import com.vcampus.client.config.AppConfig;
import com.vcampus.client.config.AppConfigManager;
import com.vcampus.client.config.ConfigPathUtil;
import com.vcampus.client.net.SocketClient;
import com.vcampus.client.util.MonetColorUtil;
import com.vcampus.client.util.ScrollSpeedUtil;
import com.vcampus.client.util.ThemeManager;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
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
            new LinkedBlockingQueue<>(20),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "Settings-Thread-" + threadNumber.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.DiscardPolicy()
    );

    private final AtomicBoolean connectionCheckRunning = new AtomicBoolean(false);
    /** 阻止初始化期间触发预览事件覆盖配置 */
    private boolean isInitializing = true;

    @FXML private ScrollPane settingsScrollPane;

    // --- 主题与外观 ---
    @FXML private ToggleGroup themeToggleGroup;
    @FXML private RadioButton themeLightRadio;
    @FXML private RadioButton themeDarkRadio;
    @FXML private RadioButton themeSystemRadio;
    @FXML private HBox colorPaletteGroup;

    @FXML private Button monetExtractBtn;
    @FXML private StackPane monetColorContainer;
    @FXML private Region monetColorCircle;
    @FXML private Label monetColorTip;

    @FXML private TextField bgPathField;
    @FXML private VBox bgOpacityBox;
    @FXML private Slider bgOpacitySlider;
    @FXML private Label bgOpacityValueLabel;

    // --- 网络通信 ---
    @FXML private TextField serverHostField;
    @FXML private TextField serverPortField;
    @FXML private TextField connectTimeoutField;
    @FXML private ProgressIndicator testingSpinner;
    @FXML private Label testResultLabel;

    // --- 交互与行为 ---
    @FXML private Slider scrollSpeedSlider;
    @FXML private Label scrollSpeedValueLabel;
    @FXML private ComboBox<String> closeBehaviorCombo;
    @FXML private CheckBox rememberCardNumCheck;

    @FXML private TextField configFilePathField;
    @FXML private Label statusMessageLabel;

    private static final Map<String, String> PRESET_COLORS = new LinkedHashMap<>();

    static {
        PRESET_COLORS.put("东南绿", "#487A32");
        PRESET_COLORS.put("飞书蓝", "#3370FF");
        PRESET_COLORS.put("活力橙", "#FF7D00");
        PRESET_COLORS.put("优雅紫", "#722ED1");
        PRESET_COLORS.put("商务深灰", "#333333");
    }

    private String selectedAccentColor = "#487A32";
    private String currentCustomBgPath = "";
    private String currentMonetColor = "";

    /**
     * 构建一个临时的预览配置对象，避免直接修改全局配置
     */
    private AppConfig buildPreviewConfig() {
        AppConfig source = AppConfigManager.getInstance().getConfig();
        AppConfig preview = new AppConfig();
        preview.setCardNum(source.getCardNum());
        preview.setServerHost(source.getServerHost());
        preview.setServerPort(source.getServerPort());
        preview.setConnectTimeoutMs(source.getConnectTimeoutMs());
        preview.setThemeMode(source.getThemeMode());
        preview.setAccentColor(source.getAccentColor());
        preview.setCustomBgPath(source.getCustomBgPath());
        preview.setBgScrimOpacity(source.getBgScrimOpacity());
        preview.setUseMonetTheme(source.isUseMonetTheme());
        preview.setSavedMonetColor(source.getSavedMonetColor());
        preview.setScrollSpeedFactor(source.getScrollSpeedFactor());
        preview.setCloseBehavior(source.getCloseBehavior());
        preview.setRememberCardNum(source.isRememberCardNum());
        preview.setLastCardNum(source.getLastCardNum());
        return preview;
    }

    @FXML
    public void initialize() {
        // 绑定平滑滚动
        if (settingsScrollPane != null) {
            ScrollSpeedUtil.applyCustomScrollSpeed(settingsScrollPane);
        }

        monetColorContainer.managedProperty().bind(monetColorContainer.visibleProperty());
        monetColorTip.managedProperty().bind(monetColorTip.visibleProperty());

        initCloseBehaviorOptions();
        initColorPalette();
        initMonetCircleAction();

        // 必须先加载配置，再绑定监听器，防止互相覆盖
        loadCurrentConfigToUI();

        initThemeToggleListener();
        initSliderListeners();
        isInitializing = false;
    }

    private void initThemeToggleListener() {
        themeToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                previewTheme();
            }
        });
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
                previewTheme();
            });
            colorPaletteGroup.getChildren().add(circle);
        }
    }

    private void initMonetCircleAction() {
        monetColorCircle.setOnMouseClicked(event -> {
            if (currentMonetColor != null && !currentMonetColor.isEmpty()) {
                selectedAccentColor = currentMonetColor;
                updatePaletteSelection();
                previewTheme();
            }
        });
    }

    private void initSliderListeners() {
        scrollSpeedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double formatted = Math.round(newVal.doubleValue() * 10.0) / 10.0;
            scrollSpeedValueLabel.setText(formatted + "x");
        });

        bgOpacitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int percent = (int) Math.round(newVal.doubleValue() * 100);
            bgOpacityValueLabel.setText(percent + "%");
            previewTheme();
        });
    }

    private void updatePaletteSelection() {
        for (int i = 0; i < colorPaletteGroup.getChildren().size(); i++) {
            Region circle = (Region) colorPaletteGroup.getChildren().get(i);
            String hex = (String) PRESET_COLORS.values().toArray()[i];
            circle.getStyleClass().remove("selected");
            if (hex.equalsIgnoreCase(selectedAccentColor)) {
                circle.getStyleClass().add("selected");
            }
        }
        monetColorCircle.getStyleClass().remove("selected");
        if (selectedAccentColor.equalsIgnoreCase(currentMonetColor) && !currentMonetColor.isEmpty()) {
            monetColorCircle.getStyleClass().add("selected");
        }
    }

    /**
     * 从配置中心加载数据渲染到 UI 控件
     */
    public void loadCurrentConfigToUI() {
        AppConfig config = AppConfigManager.getInstance().getConfig();

        // 将状态安全载入变量（避免受 UI 默认值影响）
        selectedAccentColor = config.getAccentColor();
        currentCustomBgPath = config.getCustomBgPath();
        currentMonetColor = config.getSavedMonetColor() != null ? config.getSavedMonetColor() : "";

        // 外观
        String themeMode = config.getThemeMode();
        if ("dark".equalsIgnoreCase(themeMode)) {
            themeDarkRadio.setSelected(true);
        } else if ("system".equalsIgnoreCase(themeMode)) {
            themeSystemRadio.setSelected(true);
        } else {
            themeLightRadio.setSelected(true);
        }

        bgPathField.setText(currentCustomBgPath != null ? currentCustomBgPath : "");

        double opacity = config.getBgScrimOpacity();
        bgOpacitySlider.setValue(opacity);
        bgOpacityValueLabel.setText((int) Math.round(opacity * 100) + "%");

        refreshMonetState();

        // 网络
        serverHostField.setText(config.getServerHost());
        serverPortField.setText(String.valueOf(config.getServerPort()));
        connectTimeoutField.setText(String.valueOf(config.getConnectTimeoutMs()));

        // 交互
        scrollSpeedSlider.setValue(config.getScrollSpeedFactor());
        scrollSpeedValueLabel.setText(config.getScrollSpeedFactor() + "x");
        closeBehaviorCombo.getSelectionModel().select("minimize".equalsIgnoreCase(config.getCloseBehavior()) ? 1 : 0);
        rememberCardNumCheck.setSelected(config.isRememberCardNum());

        // 存储路径
        File currentFile = ConfigPathUtil.getConfigFile(config.getCardNum());
        configFilePathField.setText(currentFile.getAbsolutePath());
    }

    /**
     * 独立校验背景图并控制莫奈按钮组件显示
     */
    private void refreshMonetState() {
        if (currentCustomBgPath != null && !currentCustomBgPath.trim().isEmpty()) {
            File bgFile = new File(currentCustomBgPath);
            if (bgFile.exists() && bgFile.isFile()) {
                monetExtractBtn.setDisable(false);
                if (currentMonetColor != null && !currentMonetColor.isEmpty()) {
                    monetColorCircle.setStyle("-fx-background-color: " + currentMonetColor + ";");
                }
                monetColorContainer.setVisible(currentMonetColor != null && !currentMonetColor.isEmpty());
                monetColorTip.setVisible(currentMonetColor != null && !currentMonetColor.isEmpty());
                updatePaletteSelection();
                return;
            }
        }
        // 无有效图片时，禁用按钮、隐藏圆圈
        currentMonetColor = "";
        monetExtractBtn.setDisable(true);
        monetColorContainer.setVisible(false);
        monetColorTip.setVisible(false);
        updatePaletteSelection();
    }

    @FXML
    private void handleChooseBackground() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择自定义背景图片");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("图像文件 (*.jpg, *.png, *.jpeg)", "*.jpg", "*.png", "*.jpeg")
        );
        File selectedFile = fileChooser.showOpenDialog(settingsScrollPane.getScene().getWindow());
        if (selectedFile != null) {
            currentCustomBgPath = selectedFile.getAbsolutePath();
            selectedAccentColor = PRESET_COLORS.values().iterator().next();
            currentMonetColor = "";
            bgPathField.setText(currentCustomBgPath);
            // 仅解锁莫奈按钮状态，让用户自己选择是否取色，不覆盖当前配置颜色
            refreshMonetState();
            previewTheme();
        }
    }

    @FXML
    private void handleExtractMonetColor() {
        if (currentCustomBgPath != null && !currentCustomBgPath.trim().isEmpty()) {
            File bgFile = new File(currentCustomBgPath);
            if (bgFile.exists() && bgFile.isFile()) {
                currentMonetColor = MonetColorUtil.extractSeedColor(bgFile);
                monetColorCircle.setStyle("-fx-background-color: " + currentMonetColor + ";");
                monetColorContainer.setVisible(true);
                monetColorTip.setVisible(true);
                selectedAccentColor = currentMonetColor;
                updatePaletteSelection();
                previewTheme();
            }
        }
    }

    @FXML
    private void handleClearBackground() {
        currentCustomBgPath = "";
        selectedAccentColor = PRESET_COLORS.values().iterator().next();
        currentMonetColor = "";
        bgPathField.clear();
        refreshMonetState();
        previewTheme();
    }


    private void previewTheme() {
        if (isInitializing) {
            return; // 阻止初始化相互打架
        }

        AppConfig config = buildPreviewConfig();
        config.setAccentColor(selectedAccentColor);

        if (themeDarkRadio.isSelected()) {
            config.setThemeMode("dark");
        } else if (themeSystemRadio.isSelected()) {
            config.setThemeMode("system");
        } else {
            config.setThemeMode("light");
        }

        config.setCustomBgPath(currentCustomBgPath);
        config.setBgScrimOpacity(bgOpacitySlider.getValue());

        if (settingsScrollPane != null && settingsScrollPane.getScene() != null) {
            ThemeManager.applyTheme(settingsScrollPane.getScene(), config);
        }
    }

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
            config.setCustomBgPath(currentCustomBgPath);
            config.setBgScrimOpacity(bgOpacitySlider.getValue());
            config.setSavedMonetColor(currentMonetColor != null ? currentMonetColor : "");

            double speedFactor = Math.round(scrollSpeedSlider.getValue() * 10.0) / 10.0;
            config.setScrollSpeedFactor(speedFactor);
            config.setCloseBehavior(closeBehaviorCombo.getSelectionModel().getSelectedIndex() == 1 ? "minimize" : "exit");
            config.setRememberCardNum(rememberCardNumCheck.isSelected());

            boolean ok = AppConfigManager.getInstance().saveConfig();
            ScrollSpeedUtil.SPEED_MULTIPLIER.set(speedFactor);

            if (ok) {
                previewTheme();
                showStatusMessage("偏好设置与个性化壁纸已成功保存！");
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
            try (SocketClient client = new SocketClient(host, port)) {
                Message ping = new Message("probe", MessageType.HEARTBEAT, null, "ping");
                Message pong = client.send(ping);
                reachable = pong != null && "pong".equals(pong.getData());
            } catch (Exception ignored) {
                // 连接失败或超时，视为不可达
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
        config.setCustomBgPath(defaultConfig.getCustomBgPath());
        config.setBgScrimOpacity(defaultConfig.getBgScrimOpacity());
        config.setSavedMonetColor(defaultConfig.getSavedMonetColor());
        config.setScrollSpeedFactor(defaultConfig.getScrollSpeedFactor());
        config.setCloseBehavior(defaultConfig.getCloseBehavior());
        config.setRememberCardNum(defaultConfig.isRememberCardNum());

        AppConfigManager.getInstance().saveConfig();
        ScrollSpeedUtil.SPEED_MULTIPLIER.set(defaultConfig.getScrollSpeedFactor());

        isInitializing = true;
        loadCurrentConfigToUI();
        isInitializing = false;

        previewTheme();
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

        isInitializing = true;
        loadCurrentConfigToUI();
        isInitializing = false;

        previewTheme();
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