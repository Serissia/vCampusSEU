package com.vcampus.client.controller;

import com.vcampus.client.net.SocketClient;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.UserVO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
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
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

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

        // 绑定输入框回车触发逻辑
        passwordField.setOnAction(event -> handleLogin());
        usernameField.setOnAction(event -> passwordField.requestFocus());
    }

    /**
     * 响应登录按钮点击及回车提交事件
     */
    @FXML
    public void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showError("用户名或密码不能为空");
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
                        navigateToMainView(currentUser);
                    } else if (responseMsg != null && responseMsg.getCode() == ResponseCode.UNAUTHORIZED) {
                        showError("用户名或密码错误");
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
            stage.centerOnScreen();
        } catch (IOException e) {
            showError("主界面加载失败：" + e.getMessage());
        }
    }

    private void setLoading(boolean isLoading) {
        loginButton.setVisible(!isLoading);
        loadingIndicator.setVisible(isLoading);
        usernameField.setDisable(isLoading);
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