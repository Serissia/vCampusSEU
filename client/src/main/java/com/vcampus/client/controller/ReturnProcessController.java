package com.vcampus.client.controller;

import com.vcampus.client.net.SocketClient;
import com.vcampus.client.util.ScrollSpeedUtil;
import com.vcampus.client.util.SvgIcons;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.BorrowRecordVO;
import com.vcampus.common.vo.UserVO;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 图书办理归还控制器。
 *
 * <p>系统管理员输入借阅人学号 / 工号查询其借阅记录，并为借阅人办理归还。</p>
 *
 * @author GGbongy
 */
public class ReturnProcessController {

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
                    Thread thread = new Thread(r, "ReturnProcess-Thread-" + threadNumber.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @FXML
    private Button backButton;
    @FXML
    private ScrollPane rootScrollPane;
    @FXML
    private TextField studentIdField;
    @FXML
    private TableView<BorrowRecordVO> borrowTable;
    @FXML
    private Label msgLabel;

    private UserVO currentUser;
    private final SocketClient socketClient = new SocketClient();
    private Runnable backAction;

    @FXML
    private void initialize() {
        if (rootScrollPane != null) {
            ScrollSpeedUtil.applyCustomScrollSpeed(rootScrollPane);
        }
        backButton.setGraphic(SvgIcons.createIcon("arrow-left", 13, "back-icon"));
        backButton.setGraphicTextGap(6.0);
        setupTable();
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

    /**
     * 构造借阅记录表格列，并附归还操作按钮。
     */
    private void setupTable() {
        TableColumn<BorrowRecordVO, String> titleCol = new TableColumn<>("书名");
        titleCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getTitle()));
        titleCol.setPrefWidth(190);

        TableColumn<BorrowRecordVO, String> isbnCol = new TableColumn<>("ISBN");
        isbnCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getIsbn()));
        isbnCol.setPrefWidth(130);

        TableColumn<BorrowRecordVO, String> borrowCol = new TableColumn<>("借出日期");
        borrowCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getBorrowDate()));
        borrowCol.setPrefWidth(105);

        TableColumn<BorrowRecordVO, String> dueCol = new TableColumn<>("应还日期");
        dueCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getDueDate()));
        dueCol.setPrefWidth(105);

        TableColumn<BorrowRecordVO, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getStatus()));
        statusCol.setCellFactory(col -> new TableCell<BorrowRecordVO, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    boolean borrowed = "BORROWED".equals(item);
                    setText(borrowed ? "借阅中" : "已归还");
                    getStyleClass().removeAll("status-borrowed", "status-returned");
                    getStyleClass().add(borrowed ? "status-borrowed" : "status-returned");
                }
            }
        });
        statusCol.setPrefWidth(90);

        TableColumn<BorrowRecordVO, String> actionCol = new TableColumn<>("操作");
        actionCol.setCellFactory(col -> new TableCell<BorrowRecordVO, String>() {
            private final Button btn = new Button("归还");

            {
                btn.getStyleClass().add("lib-btn-return");
                btn.setOnAction(e -> {
                    BorrowRecordVO record = getTableRow().getItem();
                    if (record != null) {
                        returnBook(record);
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                BorrowRecordVO record = getTableRow().getItem();
                if (empty || record == null) {
                    setGraphic(null);
                } else {
                    btn.setDisable(!"BORROWED".equals(record.getStatus()));
                    setGraphic(btn);
                }
            }
        });
        actionCol.setPrefWidth(80);

        borrowTable.getColumns().addAll(titleCol, isbnCol, borrowCol, dueCol, statusCol, actionCol);
        borrowTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        for (TableColumn<BorrowRecordVO, ?> col : borrowTable.getColumns()) {
            col.setMinWidth(80.0);
        }
    }

    /**
     * 按借阅人学号查询其借阅记录。
     */
    @FXML
    private void handleQuery() {
        String studentId = studentIdField.getText() == null ? "" : studentIdField.getText().trim();
        if (studentId.isEmpty()) {
            showMsg("请先输入借阅人学号 / 工号", false);
            return;
        }

        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.BORROW_BY_STUDENT, null, studentId);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS
                            && response.getData() instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<BorrowRecordVO> records = (List<BorrowRecordVO>) response.getData();
                        borrowTable.getItems().setAll(records);
                        showMsg(records.isEmpty() ? "该借阅人暂无借阅记录" : "共 " + records.size() + " 条借阅记录", true);
                    } else {
                        borrowTable.getItems().clear();
                        showMsg("查询失败，请核对借阅人学号", false);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("网络错误", "无法连接服务器: " + e.getMessage(), Alert.AlertType.ERROR));
            }
        });
    }

    /**
     * 为借阅人办理归还。
     */
    private void returnBook(BorrowRecordVO record) {
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.BOOK_RETURN, null,
                        new String[]{record.getStudentId(), record.getIsbn()});
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS) {
                        showMsg("《" + record.getTitle() + "》已归还", true);
                        handleQuery();
                    } else {
                        showMsg(resolveBorrowError(response), false);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("网络错误", "无法连接服务器: " + e.getMessage(), Alert.AlertType.ERROR));
            }
        });
    }

    /**
     * 将借还业务状态码转换为用户可读提示。
     */
    private String resolveBorrowError(Message response) {
        if (response == null) {
            return "请求被服务器拒绝";
        }
        switch (response.getCode()) {
            case NOT_BORROWED:
                return "该借阅记录不存在或已归还";
            case INVALID_REQUEST:
                return "请求参数不合法";
            default:
                return "归还失败，请稍后重试";
        }
    }

    private void showMsg(String msg, boolean success) {
        msgLabel.setText(msg);
        msgLabel.getStyleClass().removeAll("error", "success");
        msgLabel.getStyleClass().add(success ? "success" : "error");
        msgLabel.setVisible(true);
        msgLabel.setManaged(true);
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
