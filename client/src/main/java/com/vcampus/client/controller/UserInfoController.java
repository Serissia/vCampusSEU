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
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 用户信息管理控制器（仅系统管理员）。
 *
 * <p>列出所有用户，可编辑账号、姓名、角色、状态，支持冻结/解冻与删除。</p>
 *
 * @author GGbongy
 */
public class UserInfoController {

    private static final ExecutorService THREAD_POOL = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "UserInfo-Thread-" + threadNumber.getAndIncrement());
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
    private TableView<UserVO> userTable;
    @FXML
    private TextField uidField;
    @FXML
    private TextField nameField;
    @FXML
    private ComboBox<UserRole> roleComboBox;
    @FXML
    private ComboBox<String> statusComboBox;
    @FXML
    private Label msgLabel;

    private UserVO currentUser;
    private Runnable backAction;
    private final SocketClient socketClient = new SocketClient();

    /** 当前选中的用户（用于记录原账号，便于删除与更新定位） */
    private UserVO selectedUser;

    @FXML
    private void initialize() {
        if (rootScrollPane != null) {
            ScrollSpeedUtil.applyCustomScrollSpeed(rootScrollPane);
        }
        backButton.setGraphic(SvgIcons.createIcon("arrow-left", 13, "back-icon"));
        backButton.setGraphicTextGap(6.0);
        setupTable();
        setupComboBoxes();
    }

    public void initData(UserVO user, Runnable backAction) {
        this.currentUser = user;
        this.backAction = backAction;
        refreshUsers();
    }

    private void setupTable() {
        TableColumn<UserVO, String> uidCol = new TableColumn<>("账号");
        uidCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getUid()));
        uidCol.setPrefWidth(140);

        TableColumn<UserVO, String> nameCol = new TableColumn<>("姓名");
        nameCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getName()));
        nameCol.setPrefWidth(120);

        TableColumn<UserVO, String> roleCol = new TableColumn<>("角色");
        roleCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                cd.getValue().getRole() == null ? "-" : cd.getValue().getRole().getLabel()));
        roleCol.setPrefWidth(120);

        TableColumn<UserVO, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                (cd.getValue().getStatus() != null && cd.getValue().getStatus() == 1) ? "正常" : "冻结"));
        statusCol.setPrefWidth(90);

        TableColumn<UserVO, String> balanceCol = new TableColumn<>("余额");
        balanceCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                cd.getValue().getBalance() == null ? "0.00" : cd.getValue().getBalance().toPlainString()));
        balanceCol.setPrefWidth(100);

        userTable.getColumns().addAll(uidCol, nameCol, roleCol, statusCol, balanceCol);
        userTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        for (TableColumn<UserVO, ?> col : userTable.getColumns()) {
            col.setMinWidth(80.0);
        }

        userTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadIntoForm(newVal);
            }
        });
    }

    private void setupComboBoxes() {
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

        statusComboBox.getItems().setAll("正常", "冻结");
    }

    @FXML
    private void handleBack() {
        if (backAction != null) {
            backAction.run();
        }
    }

    @FXML
    private void handleRefresh() {
        refreshUsers();
    }

    private void refreshUsers() {
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.USER_LIST, null, null);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS
                            && response.getData() instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<UserVO> users = (List<UserVO>) response.getData();
                        userTable.getItems().setAll(users);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showMsg("网络错误：" + e.getMessage(), false));
            }
        });
    }

    private void loadIntoForm(UserVO user) {
        selectedUser = user;
        uidField.setText(user.getUid());
        nameField.setText(user.getName());
        roleComboBox.setValue(user.getRole());
        statusComboBox.setValue((user.getStatus() != null && user.getStatus() == 1) ? "正常" : "冻结");
        hideMsg();
    }

    @FXML
    private void handleSave() {
        if (selectedUser == null) {
            showMsg("请先在列表中选择要修改的用户", false);
            return;
        }
        String oldUid = selectedUser.getUid();
        String newUid = uidField.getText() == null ? "" : uidField.getText().trim();
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        UserRole role = roleComboBox.getValue();
        String status = statusComboBox.getValue() == null ? "" : statusComboBox.getValue();

        if (newUid.isEmpty() || name.isEmpty() || role == null || status.isEmpty()) {
            showMsg("请完整填写账号、姓名、角色和状态", false);
            return;
        }

        String statusValue = "正常".equals(status) ? "1" : "0";
        String[] payload = new String[]{oldUid, newUid, name, role.name(), statusValue};

        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.USER_UPDATE, null, payload);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS) {
                        showMsg("保存成功", true);
                        refreshUsers();
                    } else {
                        String errMsg = "保存失败";
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

    @FXML
    private void handleDelete() {
        if (selectedUser == null) {
            showMsg("请先在列表中选择要删除的用户", false);
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认删除");
        confirm.setHeaderText(null);
        confirm.setContentText("确定删除用户「" + selectedUser.getName() + "（" + selectedUser.getUid() + "）」吗？"
                + "删除后其选课、借阅、订单等历史数据也会一并删除。");
        Optional<ButtonType> result = confirm.showAndWait();
        if (!result.isPresent() || result.get() != ButtonType.OK) {
            return;
        }

        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.USER_DELETE, null, selectedUser.getUid());
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS) {
                        showMsg("删除成功", true);
                        selectedUser = null;
                        clearForm();
                        refreshUsers();
                    } else {
                        String errMsg = "删除失败";
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

    private void clearForm() {
        uidField.clear();
        nameField.clear();
        roleComboBox.setValue(null);
        statusComboBox.setValue(null);
    }

    private void showMsg(String msg, boolean success) {
        msgLabel.setText(msg);
        msgLabel.getStyleClass().removeAll("error", "success");
        msgLabel.getStyleClass().add(success ? "success" : "error");
        msgLabel.setVisible(true);
        msgLabel.setManaged(true);
    }

    private void hideMsg() {
        msgLabel.setVisible(false);
        msgLabel.setManaged(false);
    }
}
