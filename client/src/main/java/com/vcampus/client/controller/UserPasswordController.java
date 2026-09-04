package com.vcampus.client.controller;

import com.vcampus.client.net.SocketClient;
import com.vcampus.client.util.ScrollSpeedUtil;
import com.vcampus.client.util.SvgIcons;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.UserVO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 重置用户密码控制器（仅系统管理员）。
 *
 * @author GGbongy
 */
public class UserPasswordController {

    private static final ExecutorService THREAD_POOL = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "UserPassword-Thread-" + threadNumber.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy());

    @FXML
    private ScrollPane rootScrollPane;
    @FXML
    private Button backButton;
    @FXML
    private TextField uidField;
    @FXML
    private PasswordField newPasswordField;
    @FXML
    private Label msgLabel;

    private UserVO currentUser;
    private Runnable backAction;
    private final SocketClient socketClient = new SocketClient();

    @FXML
    private void initialize() {
        if (rootScrollPane != null) {
            ScrollSpeedUtil.applyCustomScrollSpeed(rootScrollPane);
        }
        backButton.setGraphic(SvgIcons.createIcon("arrow-left", 13, "back-icon"));
        backButton.setGraphicTextGap(6.0);
    }

    public void initData(UserVO user, Runnable backAction) {
        this.currentUser = user;
        this.backAction = backAction;
    }

    @FXML
    private void handleBack() {
        if (backAction != null) {
            backAction.run();
        }
    }

    @FXML
    private void handleReset() {
        String uid = uidField.getText() == null ? "" : uidField.getText().trim();
        String newPassword = newPasswordField.getText() == null ? "" : newPasswordField.getText().trim();

        if (uid.isEmpty() || newPassword.isEmpty()) {
            showMsg("请输入账号和新密码", false);
            return;
        }
        if (newPassword.length() < 6) {
            showMsg("密码长度不能少于 6 位", false);
            return;
        }

        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.USER_RESET_PASSWORD, null,
                        new String[]{uid, newPassword});
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS) {
                        showMsg("密码重置成功", true);
                        newPasswordField.clear();
                    } else {
                        String errMsg = "重置失败，请核对账号是否存在";
                        if (response != null && response.getData() instanceof String) {
                            errMsg = (String) response.getData();
                        }
                        showMsg(errMsg, false);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showMsg("网络错误：" + e.getMessage(), false));
            }
        });
    }

    private void showMsg(String msg, boolean success) {
        msgLabel.setText(msg);
        msgLabel.getStyleClass().removeAll("error", "success");
        msgLabel.getStyleClass().add(success ? "success" : "error");
        msgLabel.setVisible(true);
        msgLabel.setManaged(true);
    }
}
