package com.vcampus.client.controller;

import com.vcampus.client.config.AppConfig;
import com.vcampus.client.config.AppConfigManager;
import com.vcampus.client.net.SocketClient;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.UserVO;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
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

    /**
     * 底层 Socket 通信客户端
     */
    private final SocketClient socketClient = new SocketClient();

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
    public void initialize() {
        // 加载 resources/images/login_bg.jpg 背景图
        loadImageSafely("/images/login_bg.jpg", bgImageView);

        setupAccountComboBox();
        loadSavedAccounts();

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
        THREAD_POOL.execute(() -> {
            AppConfigManager.getInstance().deleteUserConfig(deleteCard);
        });
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
                Message responseMsg = socketClient.send(requestMsg);

                Platform.runLater(() -> {
                    setLoading(false);
                    if (responseMsg != null && responseMsg.getCode() == ResponseCode.SUCCESS) {
                        UserVO currentUser = (UserVO) responseMsg.getData();

                        // 登录成功时统一委托给 AppConfigManager 更新保存对应用户的配置
                        AppConfigManager configManager = AppConfigManager.getInstance();
                        configManager.switchUser(currentUser.getAccountNumber());
                        AppConfig config = configManager.getConfig();
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
                    showError("无法连接至服务器，请检查服务端是否启动 (8888)");
                });
            }
        });
    }

    @FXML
    public void handleForgotPassword() {
        showError("请联系各院系教务老师或网络中心重置密码");
    }

    private void navigateToMainView(UserVO user) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
            Parent root = loader.load();

            // 注入当前登录用户的上下文信息
            MainController mainController = loader.getController();
            mainController.initUserContext(user);

            Stage stage = (Stage) loginButton.getScene().getWindow();
            Scene mainScene = new Scene(root, 1100, 720);
            stage.setTitle("vCampus - 智慧校园综合服务平台");
            stage.setScene(mainScene);
            stage.setResizable(true);
            stage.setMinWidth(1024);
            stage.setMinHeight(680);
            stage.setWidth(1200);
            stage.setHeight(800);
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
}