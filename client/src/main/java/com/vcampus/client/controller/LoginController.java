package com.vcampus.client.controller;

import com.vcampus.client.config.AppConfig;
import com.vcampus.client.config.AppConfigManager;
import com.vcampus.client.net.ClientSession;
import com.vcampus.client.net.SocketClient;
import com.vcampus.client.util.ThemeManager;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.UserVO;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 统一身份认证登录视图控制器
 * 负责收集凭据、异步鉴权网络通信及页面路由切换
 *
 * @author Serissia
 */
public class LoginController {

    /**
     * 自定义业务线程池
     */
    private static final ExecutorService THREAD_POOL = new ThreadPoolExecutor(
            2,
            4,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "Login-Thread-" + threadNumber.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @FXML
    private ImageView bgImageView;

    @FXML
    private ImageView logoImageView;

    @FXML
    private ComboBox<AppConfig> accountComboBox;

    @FXML
    private PasswordField passwordField;

    @FXML
    private CheckBox rememberPasswordCheck;

    @FXML
    private Label errorLabel;

    @FXML
    private Button loginButton;

    @FXML
    private ProgressIndicator loadingIndicator;

    @FXML
    private HBox carouselDots;

    @FXML
    private Hyperlink changeServerLink;

    /** 左侧背景轮播图资源路径（按顺序循环） */
    private static final String[] LOGIN_BG_IMAGES = {
            "/images/login_bg1.JPG",
            "/images/login_bg2.JPG",
            "/images/login_bg3.JPG",
            "/images/login_bg4.JPG"
    };

    /** 轮播切换间隔 */
    private static final Duration CAROUSEL_INTERVAL = Duration.seconds(4);

    /** 当前轮播图索引 */
    private int currentBgIndex = 0;

    /** 轮播定时器 */
    private Timeline carouselTimeline;

    @FXML
    public void initialize() {
        AppConfigManager.getInstance().switchUser(null);

        // 启动左侧背景轮播
        startBackgroundCarousel();

        setupAccountComboBox();
        loadSavedAccounts();
        updateServerLinkHint();

        // 绑定输入框回车触发逻辑
        passwordField.setOnAction(event -> handleLogin());
    }

    /**
     * 配置可编辑账号下拉框的转换器、自定义单元格及联动监听
     */
    private void setupAccountComboBox() {
        accountComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(AppConfig config) {
                return config == null ? "" : (config.getCardNum() == null ? "" : config.getCardNum());
            }

            @Override
            public AppConfig fromString(String string) {
                if (string == null || string.trim().isEmpty()) {
                    return null;
                }
                String card = string.trim();
                for (AppConfig item : accountComboBox.getItems()) {
                    if (card.equals(item.getCardNum())) {
                        return item;
                    }
                }
                AppConfig temp = new AppConfig();
                temp.setCardNum(card);
                return temp;
            }
        });

        // 定制每行：卡号、角色色块、弹性空白、删除按钮
        accountComboBox.setCellFactory(lv -> new ListCell<>() {
            private final HBox cellBox = new HBox(8);
            private final Label cardLabel = new Label();
            private final Label roleLabel = new Label();
            private final Region spacer = new Region();
            private final Button deleteButton = new Button("×");

            {
                cellBox.setAlignment(Pos.CENTER_LEFT);
                cellBox.setPadding(new Insets(4, 6, 4, 6));
                HBox.setHgrow(spacer, Priority.ALWAYS);

                cardLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: -fx-text-main;");
                roleLabel.getStyleClass().add("account-role-badge");

                deleteButton.getStyleClass().add("account-delete-btn");
                deleteButton.setTooltip(new Tooltip("删除账号记录"));
                // 禁止删除按钮获得焦点
                deleteButton.setFocusTraversable(false);

                // 在 MOUSE_PRESSED 阶段立即执行删除并阻断外层事件
                deleteButton.setOnMousePressed(e -> {
                    e.consume();
                    deleteAccount(getItem());
                });
                deleteButton.setOnMouseReleased(Event::consume);
                deleteButton.setOnMouseClicked(Event::consume);
                deleteButton.setOnAction(e -> {
                    e.consume();
                    deleteAccount(getItem());
                });

                cellBox.getChildren().addAll(cardLabel, roleLabel, spacer, deleteButton);
            }

            @Override
            protected void updateItem(AppConfig item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.getCardNum() == null || item.getCardNum().trim().isEmpty()) {
                    setGraphic(null);
                    setText(null);
                } else {
                    cardLabel.setText(item.getCardNum());
                    String r = item.getRole();
                    if (r == null || r.trim().isEmpty()) {
                        r = "用户";
                    }
                    roleLabel.setText(r);
                    setGraphic(cellBox);
                    setText(null);
                }
            }
        });

        // 切换下拉账号时的密码自动回填逻辑
        accountComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getCardNum() != null) {
                accountComboBox.getEditor().setText(newVal.getCardNum());
                if (newVal.isRememberPassword()) {
                    rememberPasswordCheck.setSelected(true);
                    passwordField.setText(newVal.getPassword() != null ? newVal.getPassword() : "");
                } else {
                    rememberPasswordCheck.setSelected(false);
                    passwordField.clear();
                }
            }
        });

        // 编辑框手动输入文字监听联动
        accountComboBox.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                passwordField.clear();
                rememberPasswordCheck.setSelected(false);
                return;
            }
            String input = newVal.trim();
            AppConfig matched = null;
            for (AppConfig item : accountComboBox.getItems()) {
                if (input.equals(item.getCardNum())) {
                    matched = item;
                    break;
                }
            }
            if (matched != null) {
                if (matched.isRememberPassword()) {
                    rememberPasswordCheck.setSelected(true);
                    passwordField.setText(matched.getPassword() != null ? matched.getPassword() : "");
                } else {
                    rememberPasswordCheck.setSelected(false);
                    passwordField.clear();
                }
            } else {
                passwordField.clear();
                rememberPasswordCheck.setSelected(false);
            }
        });

        accountComboBox.getEditor().setOnAction(event -> passwordField.requestFocus());
    }

    /**
     * 删除指定账号记录及本地配置
     * 解决视觉停留优化：先收起浮层再清理输入框，且本地磁盘 I/O 异步化
     *
     * @param item 需删除的账号配置
     */
    private void deleteAccount(AppConfig item) {
        if (item == null || item.getCardNum() == null) {
            return;
        }
        String deleteCard = item.getCardNum().trim();
        String currentInput = accountComboBox.getEditor().getText();
        boolean isCurrent = currentInput != null && deleteCard.equals(currentInput.trim());

        // 优先关闭下拉框，消除浮层慢半拍悬停的观感
        if (isCurrent || accountComboBox.getItems().size() <= 1) {
            accountComboBox.hide();
        }

        // 从下拉数据源中移除
        accountComboBox.getItems().removeIf(cfg -> deleteCard.equals(cfg.getCardNum()));

        // 将输入框及密码重置延迟至下一渲染微任务，确保下拉框完全闭合后再呈现清空状态
        if (isCurrent) {
            Platform.runLater(() -> {
                accountComboBox.getEditor().clear();
                accountComboBox.setValue(null);
                passwordField.clear();
                rememberPasswordCheck.setSelected(false);
            });
        }

        // 本地文件删除放入后台线程池，避免主线程 I/O 阻塞 UI 刷新
        THREAD_POOL.execute(() -> AppConfigManager.getInstance().deleteUserConfig(deleteCard));
    }

    /**
     * 加载本地所有已记住的账号信息并默认填充最新项
     */
    private void loadSavedAccounts() {
        List<AppConfig> savedConfigs = AppConfigManager.getInstance().getAllUserConfigs();
        accountComboBox.getItems().setAll(savedConfigs);

        if (!savedConfigs.isEmpty()) {
            AppConfig latest = savedConfigs.get(0);
            accountComboBox.setValue(latest);
            accountComboBox.getEditor().setText(latest.getCardNum());
            if (latest.isRememberPassword()) {
                rememberPasswordCheck.setSelected(true);
                passwordField.setText(latest.getPassword() != null ? latest.getPassword() : "");
            } else {
                rememberPasswordCheck.setSelected(false);
                passwordField.clear();
            }
        }
    }

    /**
     * 响应登录按钮点击及回车提交事件
     */
    @FXML
    public void handleLogin() {
        String username = accountComboBox.getEditor().getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showError("一卡通号和密码不能为空");
            return;
        }

        setLoading(true);
        hideError();

        // 提交至线程池异步执行，避免阻塞 JavaFX UI 线程
        THREAD_POOL.execute(() -> {
            try {
                UserVO loginUser = new UserVO(username, password);

                // 构造登录认证请求消息
                Message requestMsg = new Message(username, MessageType.LOGIN, null, loginUser);
                SocketClient socketClient = ClientSession.client();
                Message responseMsg = socketClient.send(requestMsg);

                Platform.runLater(() -> {
                    setLoading(false);
                    if (responseMsg != null && responseMsg.getCode() == ResponseCode.SUCCESS) {
                        UserVO currentUser = (UserVO) responseMsg.getData();

                        // 服务端已在本次登录所用的连接上绑定该用户身份，并下发了登录令牌
                        ClientSession.getInstance().begin(currentUser, responseMsg.getToken());

                        // 登录成功时统一委托给 AppConfigManager 更新保存对应用户的配置
                        AppConfigManager configManager = AppConfigManager.getInstance();
                        String loginHost = configManager.getConfig().getServerHost();
                        int loginPort = configManager.getConfig().getServerPort();
                        int loginTimeout = configManager.getConfig().getConnectTimeoutMs();
                        configManager.switchUser(currentUser.getAccountNumber());
                        AppConfig config = configManager.getConfig();
                        config.setServerHost(loginHost);
                        config.setServerPort(loginPort);
                        config.setConnectTimeoutMs(loginTimeout);
                        config.setCardNum(currentUser.getAccountNumber());
                        if (currentUser.getRole() != null) {
                            config.setRole(currentUser.getRole().getLabel());
                        }
                        boolean remember = rememberPasswordCheck.isSelected();
                        config.setRememberPassword(remember);
                        config.setPassword(remember ? password : "");
                        configManager.saveConfig();

                        navigateToMainView(currentUser);
                    } else if (responseMsg != null && responseMsg.getCode() == ResponseCode.UNAUTHORIZED) {
                        showError("一卡通号或密码错误");
                    } else if (responseMsg != null && responseMsg.getCode() == ResponseCode.ALREADY_LOGGED_IN) {
                        String conflictMsg = responseMsg.getData() instanceof String
                                ? (String) responseMsg.getData()
                                : "该账号已在别处登录，请先退出原设备后再试";
                        showError(conflictMsg);
                    } else {
                        String errMsg = "登录失败";
                        if (responseMsg != null && responseMsg.getData() instanceof String) {
                            errMsg = (String) responseMsg.getData();
                        }
                        showError(errMsg);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setLoading(false);
                    showError("无法连接至服务器，请检查服务器地址与网络连接");
                });
            }
        });
    }

    @FXML
    public void handleForgotPassword() {
        showError("请联系各院系教务老师或网络中心重置密码");
    }

    @FXML
    private void handleChangeServerAddress() {
        AppConfig config = AppConfigManager.getInstance().getConfig();

        // 构建自定义对话框内容
        TextField hostField = new TextField(config.getServerHost());
        hostField.setPromptText("服务器 IP 或域名");
        hostField.getStyleClass().add("modern-input-field");

        TextField portField = new TextField(String.valueOf(config.getServerPort()));
        portField.setPromptText("端口");
        portField.getStyleClass().add("modern-input-field");
        portField.setPrefWidth(110.0);

        Label hostLabel = new Label("服务器地址");
        hostLabel.getStyleClass().add("input-label");
        Label portLabel = new Label("端口");
        portLabel.getStyleClass().add("input-label");

        VBox hostBox = new VBox(6.0, hostLabel, hostField);
        HBox.setHgrow(hostBox, Priority.ALWAYS);

        VBox portBox = new VBox(6.0, portLabel, portField);

        HBox fields = new HBox(12.0, hostBox, portBox);
        fields.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label("保存后，后续登录请求将连接到新的服务器。");
        hint.setWrapText(true);
        hint.getStyleClass().add("server-dialog-hint");

        Label dialogError = new Label();
        dialogError.setWrapText(true);
        dialogError.setVisible(false);
        dialogError.setManaged(false);
        dialogError.getStyleClass().add("server-dialog-error");

        VBox content = new VBox(12.0, fields, hint, dialogError);
        content.setPadding(new Insets(6.0, 18.0, 6.0, 18.0));
        content.setPrefWidth(380.0);

        ButtonType cancelType = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType saveType = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("更改服务器地址");
        dialog.setHeaderText(null);
        if (loginButton.getScene() != null) {
            dialog.initOwner(loginButton.getScene().getWindow());
            dialog.getDialogPane().getStylesheets().addAll(loginButton.getScene().getRoot().getStylesheets());
            dialog.getDialogPane().getStylesheets().addAll(loginButton.getScene().getStylesheets());
        }
        dialog.getDialogPane().getStyleClass().add("server-address-dialog");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(cancelType, saveType);

        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(cancelType);
        cancelButton.setCancelButton(true);
        cancelButton.getStyleClass().add("btn-secondary-action");
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveButton.setDefaultButton(true);
        saveButton.getStyleClass().add("btn-primary-action");
        // 在保存按钮点击时进行输入验证和配置保存
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            String host = hostField.getText() == null ? "" : hostField.getText().trim();
            if (host.isEmpty()) {
                showDialogError(dialogError, "服务器地址不能为空");
                event.consume();
                return;
            }

            int port;
            try {
                port = Integer.parseInt(portField.getText() == null ? "" : portField.getText().trim());
            } catch (NumberFormatException e) {
                showDialogError(dialogError, "端口必须是有效整数");
                event.consume();
                return;
            }
            if (port <= 0 || port > 65535) {
                showDialogError(dialogError, "端口范围应为 1 到 65535");
                event.consume();
                return;
            }

            String oldHost = config.getServerHost();
            int oldPort = config.getServerPort();
            config.setServerHost(host);
            config.setServerPort(port);
            if (!AppConfigManager.getInstance().saveConfig()) {
                config.setServerHost(oldHost);
                config.setServerPort(oldPort);
                showDialogError(dialogError, "保存失败，请检查运行目录写权限");
                event.consume();
                return;
            }

            ClientSession.getInstance().reset();
            updateServerLinkHint();
        });

        dialog.showAndWait();
    }

    /**
     * 更新“更改服务器地址”超链接的悬停提示，显示当前服务器地址和端口
     */
    private void updateServerLinkHint() {
        if (changeServerLink == null) {
            return;
        }
        AppConfig config = AppConfigManager.getInstance().getConfig();
        changeServerLink.setTooltip(new Tooltip("当前服务器：" + config.getServerHost() + ":" + config.getServerPort()));
    }

    private void showDialogError(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    private void navigateToMainView(UserVO user) {
        stopBackgroundCarousel();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
            Parent root = loader.load();

            // 注入当前登录用户的上下文信息
            MainController mainController = loader.getController();
            mainController.initUserContext(user);

            Stage stage = (Stage) loginButton.getScene().getWindow();
            // 先定好最终窗口尺寸，再换场景，避免先渲染主界面再缩放导致的闪动
            stage.setResizable(true);
            stage.setMinWidth(1024);
            stage.setMinHeight(680);
            stage.setWidth(1200);
            stage.setHeight(800);
            stage.setTitle("vCampus - 智慧校园综合服务平台");

            Scene mainScene = new Scene(root, 1200, 800);
            stage.setScene(mainScene);

            // 在首次绘制前同步应用用户主题，避免先渲染默认主题再切换导致的启动闪屏
            ThemeManager.applyTheme(mainScene);
            stage.centerOnScreen();
        } catch (IOException e) {
            showError("主界面加载失败：" + e.getMessage());
        }
    }

    private void setLoading(boolean isLoading) {
        loginButton.setVisible(!isLoading);
        loadingIndicator.setVisible(isLoading);
        accountComboBox.setDisable(isLoading);
        passwordField.setDisable(isLoading);
        changeServerLink.setDisable(isLoading);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void loadImageSafely(String path, ImageView targetView) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is != null) {
                targetView.setImage(new Image(is));
            }
        } catch (Exception ignored) {
            // 资源未放置时降级为 CSS 背景
        }
    }

    /**
     * 启动左侧背景轮播：定时循环切换背景图并同步指示点。
     */
    private void startBackgroundCarousel() {
        if (LOGIN_BG_IMAGES.length == 0) {
            return;
        }
        // 首屏直接显示第一张，无需淡入
        loadImageSafely(LOGIN_BG_IMAGES[0], bgImageView);
        updateCarouselDots(0);

        carouselTimeline = new Timeline(
                new KeyFrame(CAROUSEL_INTERVAL, e -> {
                    currentBgIndex = (currentBgIndex + 1) % LOGIN_BG_IMAGES.length;
                    switchBackground(currentBgIndex);
                })
        );
        carouselTimeline.setCycleCount(Timeline.INDEFINITE);
        carouselTimeline.play();
    }

    /**
     * 淡出旧图 → 载入新图 → 淡入，并更新指示点。
     */
    private void switchBackground(int index) {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(400), bgImageView);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> {
            loadImageSafely(LOGIN_BG_IMAGES[index], bgImageView);
            updateCarouselDots(index);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(400), bgImageView);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        });
        fadeOut.play();
    }

    /**
     * 停止轮播定时器（进入主界面时调用，避免后台空转）。
     */
    private void stopBackgroundCarousel() {
        if (carouselTimeline != null) {
            carouselTimeline.stop();
        }
    }

    /**
     * 更新轮播指示点：仅当前项高亮。
     */
    private void updateCarouselDots(int activeIndex) {
        if (carouselDots == null) {
            return;
        }
        for (int i = 0; i < carouselDots.getChildren().size(); i++) {
            Node dot = carouselDots.getChildren().get(i);
            dot.getStyleClass().setAll("dot");
            if (i == activeIndex) {
                dot.getStyleClass().add("active");
            }
        }
    }
}
