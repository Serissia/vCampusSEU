package com.vcampus.client.controller;

import com.vcampus.client.net.SocketClient;
import com.vcampus.client.util.ScrollSpeedUtil;
import com.vcampus.client.util.SvgIcons;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.UserRole;
import com.vcampus.common.vo.UserVO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 注册新用户控制器（仅系统管理员）。
 *
 * @author GGbongy
 */
public class UserRegisterController {

    private static final ExecutorService THREAD_POOL = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "UserRegister-Thread-" + threadNumber.getAndIncrement());
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
    private TextField nameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private ComboBox<UserRole> roleComboBox;
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
        setupRoleComboBox();
    }

    public void initData(UserVO user, Runnable backAction) {
        this.currentUser = user;
        this.backAction = backAction;
    }

    private void setupRoleComboBox() {
        roleComboBox.getItems().setAll(UserRole.values());
        roleComboBox.setConverter(new StringConverter<UserRole>() {
            @Override
            public String toString(UserRole role) {
                return role == null ? "" : role.getLabel();
            }

            @Override
            public UserRole fromString(String string) {
                return null;
            }
        });
    }

    @FXML
    private void handleBack() {
        if (backAction != null) {
            backAction.run();
        }
    }

    @FXML
    private void handleRegister() {
        String uid = uidField.getText() == null ? "" : uidField.getText().trim();
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText().trim();
        UserRole role = roleComboBox.getValue();

        if (uid.isEmpty() || name.isEmpty() || password.isEmpty() || role == null) {
            showMsg("请完整填写账号、姓名、密码和角色", false);
            return;
        }
        if (password.length() < 6) {
            showMsg("密码长度不能少于 6 位", false);
            return;
        }

        UserVO user = new UserVO();
        user.setUid(uid);
        user.setName(name);
        user.setPassword(password);
        user.setRole(role);

        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.USER_REGISTER, null, user);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS) {
                        showMsg("注册成功", true);
                        uidField.clear();
                        nameField.clear();
                        passwordField.clear();
                        roleComboBox.setValue(null);
                    } else {
                        String errMsg = "注册失败";
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
