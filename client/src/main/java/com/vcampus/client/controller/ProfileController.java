package com.vcampus.client.controller;

import com.vcampus.client.net.ClientSession;
import com.vcampus.client.net.SocketClient;
import com.vcampus.common.vo.UserVO;
import com.vcampus.client.util.ScrollSpeedUtil;
import com.vcampus.client.util.ToastBannerUtil;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 个人信息中心控制器
 * 负责个人档案展示、一卡通在线充值、密码修改等交互
 *
 * @author Serissia
 */
public class ProfileController {

    /**
     * 业务线程池
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
                    Thread thread = new Thread(r, "Profile-Thread-" + threadNumber.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @FXML
    private Label avatarLargeText;
    @FXML
    private Label displayNameText;
    @FXML
    private Label roleBadge;
    @FXML
    private Label statusBadge;
    @FXML
    private Label uidSubtitleText;
    @FXML
    private Label cardBalanceText;
    @FXML
    private TextField customRechargeField;
    @FXML
    private PasswordField oldPasswordField;
    @FXML
    private PasswordField newPasswordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Label securityMsgLabel;
    @FXML
    private Label metaUidText;
    @FXML
    private Label metaNameText;
    @FXML
    private Label metaRoleText;
    @FXML
    private ScrollPane rootScrollPane;

    private Timeline balancePolling;

    @FXML
    public void initialize() {
        // 为当前个人信息面板的滚动容器启用加速
        if (rootScrollPane != null) {
            ScrollSpeedUtil.applyCustomScrollSpeed(rootScrollPane);
            rootScrollPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    startBalancePolling();
                } else {
                    stopBalancePolling();
                }
            });
        }
    }

    private UserVO currentUser;
    private MainController mainController;
    /** 全局共享连接：服务端把身份绑定在连接上，全客户端必须复用同一条 */
    private final SocketClient socketClient = ClientSession.client();

    /**
     * 注入上下文用户数据与主框架控制器引用
     *
     * @param user           当前登录用户
     * @param mainController 父级主控制器
     */
    public void initData(UserVO user, MainController mainController) {
        this.currentUser = user;
        this.mainController = mainController;
        refreshUserData();
    }

    /**
     * 启动余额轮询任务，每秒向服务端请求最新余额并更新界面显示
     * 仅在当前用户已登录时启动，未登录状态下不进行轮询
     */
    private void startBalancePolling() {
        stopBalancePolling();
        if (currentUser == null) {
            return;
        }
        balancePolling = new Timeline(new KeyFrame(Duration.seconds(1), e -> refreshBalance()));
        balancePolling.setCycleCount(Timeline.INDEFINITE);
        balancePolling.play();
    }

    /**
     * 停止余额轮询任务，释放资源
     * 当前用户退出登录或界面被销毁时调用，避免内存泄漏和无效网络请求
     */
    private void stopBalancePolling() {
        if (balancePolling != null) {
            balancePolling.stop();
            balancePolling = null;
        }
    }

    private void refreshBalance() {
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.PAYMENT_BALANCE, null, null);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS
                            && response.getData() instanceof UserVO fresh) {
                        currentUser.setBalance(fresh.getBalance());
                        updateBalanceDisplay();
                        if (mainController != null) {
                            BigDecimal balance = fresh.getBalance() == null ? BigDecimal.ZERO : fresh.getBalance();
                            mainController.updateBalance(balance);
                        }
                    }
                });
            } catch (Exception ignored) {
                // 后台余额轮询失败不打断当前操作
            }
        });
    }

    /**
     * 渲染视图数据
     */
    private void refreshUserData() {
        if (currentUser == null) {
            return;
        }

        String name = currentUser.getName() != null && !currentUser.getName().trim().isEmpty()
                ? currentUser.getName() : currentUser.getAccountNumber();
        displayNameText.setText(name);
        avatarLargeText.setText(name.substring(0, 1).toUpperCase());
        uidSubtitleText.setText("一卡通账号 / UID: " + currentUser.getAccountNumber());

        if (currentUser.getRole() != null) {
            roleBadge.setText(currentUser.getRole().getLabel());
            metaRoleText.setText(currentUser.getRole().getLabel());
        }

        metaUidText.setText(currentUser.getAccountNumber());
        metaNameText.setText(name);

        updateBalanceDisplay();
    }

    /**
     * 刷新余额展示
     */
    private void updateBalanceDisplay() {
        BigDecimal balance = currentUser.getBalance() != null ? currentUser.getBalance() : BigDecimal.ZERO;
        cardBalanceText.setText("¥ " + balance.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    @FXML
    private void handleQuickRecharge50() {
        executeRecharge(new BigDecimal("50.00"));
    }
    @FXML
    private void handleQuickRecharge100() {
        executeRecharge(new BigDecimal("100.00"));
    }
    @FXML
    private void handleQuickRecharge200() {
        executeRecharge(new BigDecimal("200.00"));
    }

    @FXML
    private void handleCustomRecharge() {
        String amountStr = customRechargeField.getText().trim();
        if (amountStr.isEmpty()) {
            ToastBannerUtil.showToastBanner(rootScrollPane, "请输入充值金额", 2);
            return;
        }

        try {
            BigDecimal amount = new BigDecimal(amountStr);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                ToastBannerUtil.showToastBanner(rootScrollPane, "充值金额必须大于 0", 2);
                return;
            }
            executeRecharge(amount);
            customRechargeField.clear();
        } catch (NumberFormatException e) {
            ToastBannerUtil.showToastBanner(rootScrollPane, "请输入合法的数字金额", 1);
        }
    }

    /**
     * 执行在线充值（异步向服务器发起同步请求）
     *
     * @param amount 充值数额
     */
    private void executeRecharge(BigDecimal amount) {
        BigDecimal current = currentUser.getBalance() != null ? currentUser.getBalance() : BigDecimal.ZERO;
        BigDecimal targetBalance = current.add(amount);

        THREAD_POOL.execute(() -> {
            try {
                // 构造 UPDATE_USER_INFO 消息请求
                Message requestMsg = new Message(currentUser.getAccountNumber(), MessageType.UPDATE_USER_INFO, null, targetBalance);
                Message responseMsg = socketClient.send(requestMsg);

                Platform.runLater(() -> {
                    if (responseMsg != null && responseMsg.getCode() == ResponseCode.SUCCESS) {
                        currentUser.setBalance(targetBalance);
                        updateBalanceDisplay();

                        if (mainController != null) {
                            mainController.updateBalance(targetBalance);
                        }
                        ToastBannerUtil.showToastBanner(rootScrollPane,
                                "成功充值 ¥ " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
                                        + "，服务端余额已更新为 ¥ " + targetBalance.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                                0);
                    } else {
                        String errMsg = (responseMsg != null && responseMsg.getData() instanceof String)
                                ? (String) responseMsg.getData() : "充值请求被服务器拒绝";
                        ToastBannerUtil.showToastBanner(rootScrollPane, "充值失败：" + errMsg, 1);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> ToastBannerUtil.showToastBanner(rootScrollPane,
                        "无法连接服务器，充值失败：" + e.getMessage(), 1));
            }
        });
    }

    /**
     * 处理修改密码提交（前端校验 + 异步网络持久化）
     */
    @FXML
    private void handleChangePassword() {
        String oldPwd = oldPasswordField.getText().trim();
        String newPwd = newPasswordField.getText().trim();
        String confirmPwd = confirmPasswordField.getText().trim();

        if (oldPwd.isEmpty() || newPwd.isEmpty() || confirmPwd.isEmpty()) {
            showSecurityMsg("所有密码字段均不能为空", false);
            return;
        }

        if (!oldPwd.equals(currentUser.getPassword())) {
            showSecurityMsg("原密码验证错误，请重新输入", false);
            return;
        }

        if (newPwd.length() < 6) {
            showSecurityMsg("新密码长度不能少于 6 位", false);
            return;
        }

        if (newPwd.equals(oldPwd)) {
            showSecurityMsg("新密码不能与原密码相同，请设置新的密码", false);
            return;
        }

        if (!newPwd.equals(confirmPwd)) {
            showSecurityMsg("两次输入的新密码不一致", false);
            return;
        }

        THREAD_POOL.execute(() -> {
            try {
                String[] pwdPayload = new String[]{oldPwd, newPwd};
                Message requestMsg = new Message(currentUser.getAccountNumber(), MessageType.CHANGE_PASSWORD, null, pwdPayload);
                Message responseMsg = socketClient.send(requestMsg);

                Platform.runLater(() -> {
                    if (responseMsg != null && responseMsg.getCode() == ResponseCode.SUCCESS) {
                        currentUser.setPassword(newPwd);
                        oldPasswordField.clear();
                        newPasswordField.clear();
                        confirmPasswordField.clear();
                        showSecurityMsg("密码修改成功！下次登录请使用新密码", true);
                    } else {
                        String errMsg = (responseMsg != null && responseMsg.getData() instanceof String)
                                ? (String) responseMsg.getData() : "密码修改失败";
                        showSecurityMsg(errMsg, false);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showSecurityMsg("无法连接到服务端: " + e.getMessage(), false));
            }
        });
    }

    /**
     * 用于修改密码操作的提示信息显示
     *
     * @param msg 提示信息
     * @param isSuccess 是否成功
     */
    private void showSecurityMsg(String msg, boolean isSuccess) {
        securityMsgLabel.setText(msg);
        securityMsgLabel.getStyleClass().removeAll("error", "success");
        securityMsgLabel.getStyleClass().add(isSuccess ? "success" : "error");
        securityMsgLabel.setVisible(true);
        securityMsgLabel.setManaged(true);
    }
}
