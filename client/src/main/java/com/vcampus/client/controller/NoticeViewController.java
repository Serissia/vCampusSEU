package com.vcampus.client.controller;

import com.vcampus.client.net.SocketClient;
import com.vcampus.client.util.ScrollSpeedUtil;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.NoticeQueryVO;
import com.vcampus.common.vo.NoticeStatusVO;
import com.vcampus.common.vo.NoticeVO;
import com.vcampus.common.vo.UserVO;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.awt.Desktop;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 教务公告栏视图控制器。
 *
 * @author Serissia
 */
public class NoticeViewController {

    /**
     * 业务异步线程池
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
                    Thread thread = new Thread(r, "NoticeView-Thread-" + threadNumber.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    /**
     * 根滚动面板
     */
    @FXML
    private ScrollPane rootScrollPane;

    /**
     * 上次同步时间展示标签
     */
    @FXML
    private Label lastSyncLabel;

    /**
     * 同步状态徽标
     */
    @FXML
    private Label syncStatusBadge;

    /**
     * 手动爬取天数选择下拉框
     */
    @FXML
    private ComboBox<String> syncDaysCombo;

    /**
     * 手动同步触发按钮
     */
    @FXML
    private Button syncButton;

    /**
     * 同步加载转圈指示器
     */
    @FXML
    private ProgressIndicator syncSpinner;

    /**
     * 标题搜索输入框
     */
    @FXML
    private TextField keywordField;

    /**
     * 起始发布日期选择器
     */
    @FXML
    private DatePicker startDatePicker;

    /**
     * 截止发布日期选择器
     */
    @FXML
    private DatePicker endDatePicker;

    /**
     * 表格概要信息标签
     */
    @FXML
    private Label tableSummaryLabel;

    /**
     * 公告数据表格
     */
    @FXML
    private TableView<NoticeVO> noticeTable;

    /**
     * 当前登录用户档案
     */
    private UserVO currentUser;

    /**
     * 网络通信客户端
     */
    private final SocketClient socketClient = new SocketClient();

    @FXML
    private void initialize() {
        if (rootScrollPane != null) {
            ScrollSpeedUtil.applyCustomScrollSpeed(rootScrollPane);
        }
        setupDaysComboBox();
        setupNoticeTable();
    }

    /**
     * 初始化控制器数据并拉取首屏内容
     *
     * @param user 当前用户
     */
    public void initData(UserVO user) {
        this.currentUser = user;
        refreshSyncStatus();
        handleSearch();
    }

    /**
     * 初始化爬取天数下拉列表
     */
    private void setupDaysComboBox() {
        syncDaysCombo.setItems(FXCollections.observableArrayList("近 3 天", "近 7 天", "近 15 天", "近 30 天", "近 120 天"));
        syncDaysCombo.setValue("近 7 天");
    }

    /**
     * 构建表格列与双击行跳转事件
     */
    private void setupNoticeTable() {
        TableColumn<NoticeVO, String> dateCol = new TableColumn<>("发布日期");
        dateCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getPublishDate()));
        dateCol.setPrefWidth(110.0);

        TableColumn<NoticeVO, String> categoryCol = new TableColumn<>("栏目");
        categoryCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getCategory()));
        categoryCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(item);
                    badge.getStyleClass().add("notice-category-tag");
                    setGraphic(badge);
                    setText(null);
                }
            }
        });
        categoryCol.setPrefWidth(100.0);

        TableColumn<NoticeVO, String> titleCol = new TableColumn<>("公告标题");
        titleCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getTitle()));
        titleCol.setPrefWidth(420.0);

        TableColumn<NoticeVO, String> crawlTimeCol = new TableColumn<>("抓取时间");
        crawlTimeCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getCrawledTime()));
        crawlTimeCol.setPrefWidth(150.0);

        TableColumn<NoticeVO, String> actionCol = new TableColumn<>("操作");
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("查看原文");

            {
                btn.getStyleClass().add("lib-btn-borrow");
                btn.setOnAction(e -> {
                    NoticeVO notice = getTableRow().getItem();
                    if (notice != null && notice.getUrl() != null) {
                        openUrlInBrowser(notice.getUrl());
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(btn);
                }
            }
        });
        actionCol.setPrefWidth(100.0);

        noticeTable.getColumns().addAll(dateCol, categoryCol, titleCol, crawlTimeCol, actionCol);
        noticeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // 双击行直接打开原文网页
        noticeTable.setRowFactory(tv -> {
            TableRow<NoticeVO> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    NoticeVO item = row.getItem();
                    if (item != null && item.getUrl() != null) {
                        openUrlInBrowser(item.getUrl());
                    }
                }
            });
            return row;
        });
    }

    /**
     * 执行按条件搜索
     */
    @FXML
    public void handleSearch() {
        String keyword = keywordField.getText() == null ? "" : keywordField.getText().trim();
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();

        NoticeQueryVO query = new NoticeQueryVO();
        query.setKeyword(keyword);
        if (start != null) {
            query.setStartDate(start.toString());
        }
        if (end != null) {
            query.setEndDate(end.toString());
        }

        THREAD_POOL.execute(() -> {
            try {
                String account = currentUser != null ? currentUser.getAccountNumber() : "anonymous";
                Message request = new Message(account, MessageType.NOTICE_QUERY, null, query);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS
                            && response.getData() instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<NoticeVO> list = (List<NoticeVO>) response.getData();
                        noticeTable.getItems().setAll(list);
                        tableSummaryLabel.setText("公告列表（共 " + list.size() + " 条）");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("错误", "获取公告列表失败: " + e.getMessage(), Alert.AlertType.ERROR));
            }
        });
    }

    /**
     * 重置筛选条件并重新加载
     */
    @FXML
    private void handleResetFilter() {
        keywordField.clear();
        startDatePicker.setValue(null);
        endDatePicker.setValue(null);
        handleSearch();
    }

    /**
     * 触发手动同步爬取
     */
    @FXML
    private void handleTriggerSync() {
        int days = parseSelectedDays(syncDaysCombo.getValue());
        setSyncState(true);

        THREAD_POOL.execute(() -> {
            try {
                String account = currentUser != null ? currentUser.getAccountNumber() : "anonymous";
                Message request = new Message(account, MessageType.NOTICE_TRIGGER_SYNC, null, days);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    setSyncState(false);
                    if (response != null && response.getCode() == ResponseCode.SUCCESS) {
                        if (response.getData() instanceof NoticeStatusVO) {
                            applyStatus((NoticeStatusVO) response.getData());
                        }
                        handleSearch();
                    } else {
                        showAlert("提示", "同步已提交或抓取遇到网络波动", Alert.AlertType.INFORMATION);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setSyncState(false);
                    showAlert("异常", "与服务器通信失败: " + e.getMessage(), Alert.AlertType.ERROR);
                });
            }
        });
    }

    /**
     * 获取最新抓取状态
     */
    private void refreshSyncStatus() {
        THREAD_POOL.execute(() -> {
            try {
                String account = currentUser != null ? currentUser.getAccountNumber() : "anonymous";
                Message request = new Message(account, MessageType.NOTICE_GET_STATUS, null, null);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS
                            && response.getData() instanceof NoticeStatusVO) {
                        applyStatus((NoticeStatusVO) response.getData());
                    }
                });
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * 解析下拉天数选项
     */
    private int parseSelectedDays(String text) {
        // TODOS: 更完善的天数选取功能
        if (text == null) {
            return 7;
        }
        if (text.contains("30")) {
            return 30;
        }
        if (text.contains("120")) {
            return 120;
        }
        if (text.contains("15")) {
            return 15;
        }
        if (text.contains("3")) {
            return 3;
        }
        return 7;
    }

    /**
     * 更新同步状态卡片展示
     */
    private void applyStatus(NoticeStatusVO status) {
        lastSyncLabel.setText("上次同步时间：" + status.getLastSyncTime());
        if (status.isSyncing()) {
            syncStatusBadge.setText("抓取中");
            syncStatusBadge.getStyleClass().removeAll("syncing");
            syncStatusBadge.getStyleClass().add("syncing");
        } else {
            syncStatusBadge.setText("已就绪");
            syncStatusBadge.getStyleClass().removeAll("syncing");
        }
    }

    /**
     * 控制加载中状态
     */
    private void setSyncState(boolean syncing) {
        syncButton.setDisable(syncing);
        syncDaysCombo.setDisable(syncing);
        syncSpinner.setVisible(syncing);
        if (syncing) {
            syncStatusBadge.setText("同步中...");
            syncStatusBadge.getStyleClass().removeAll("syncing");
            syncStatusBadge.getStyleClass().add("syncing");
        }
    }

    /**
     * 唤醒系统默认浏览器打开链接
     */
    private void openUrlInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                showAlert("提示", "当前运行环境不支持直接唤醒浏览器，请手动复制链接：\n" + url, Alert.AlertType.INFORMATION);
            }
        } catch (Exception e) {
            showAlert("打开失败", "无法唤起系统浏览器: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}