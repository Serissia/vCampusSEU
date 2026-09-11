package com.vcampus.client.controller;

import com.vcampus.client.net.ClientSession;
import com.vcampus.client.net.SocketClient;
import com.vcampus.client.util.ScrollSpeedUtil;
import com.vcampus.client.util.SvgIcons;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.EbookSubmissionVO;
import com.vcampus.common.vo.UserVO;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 电子资源投稿审核控制器（图书管理员 / 系统管理员）。
 *
 * @author GGbongy
 */
public class EbookReviewController {

    private static final ExecutorService THREAD_POOL = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "EbookReview-Thread-" + threadNumber.getAndIncrement());
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
    private TableView<EbookSubmissionVO> pendingTable;
    @FXML
    private TextField commentField;
    @FXML
    private Label msgLabel;

    private UserVO currentUser;
    private Runnable backAction;
    private BiConsumer<String, String> browseAction;
    /** 全局共享连接：服务端把身份绑定在连接上，全客户端必须复用同一条 */
    private final SocketClient socketClient = ClientSession.client();

    @FXML
    private void initialize() {
        if (rootScrollPane != null) {
            ScrollSpeedUtil.applyCustomScrollSpeed(rootScrollPane);
        }
        backButton.setGraphic(SvgIcons.createIcon("arrow-left", 13, "back-icon"));
        backButton.setGraphicTextGap(6.0);
        setupTable();
        // 双击条目在线预览待审核 PDF
        pendingTable.setRowFactory(tv -> {
            TableRow<EbookSubmissionVO> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openPdf(row.getItem());
                }
            });
            return row;
        });
    }

    public void initData(UserVO user, Runnable backAction, BiConsumer<String, String> browseAction) {
        this.currentUser = user;
        this.backAction = backAction;
        this.browseAction = browseAction;
        refresh();
    }

    /**
     * 双击打开待审核 PDF。
     */
    private void openPdf(EbookSubmissionVO submission) {
        if (submission != null && browseAction != null) {
            browseAction.accept(submission.getResourceFile(), submission.getTitle());
        }
    }

    private void setupTable() {
        TableColumn<EbookSubmissionVO, String> titleCol = new TableColumn<>("书名");
        titleCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getTitle()));
        titleCol.setPrefWidth(160);

        TableColumn<EbookSubmissionVO, String> authorCol = new TableColumn<>("作者");
        authorCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getAuthor()));
        authorCol.setPrefWidth(110);

        TableColumn<EbookSubmissionVO, String> publisherCol = new TableColumn<>("出版社");
        publisherCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getPublisher()));
        publisherCol.setPrefWidth(140);

        TableColumn<EbookSubmissionVO, String> uploaderCol = new TableColumn<>("上传人");
        uploaderCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getUploaderUid()));
        uploaderCol.setPrefWidth(110);

        TableColumn<EbookSubmissionVO, String> descCol = new TableColumn<>("简介");
        descCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                cd.getValue().getDescription() == null ? "--" : cd.getValue().getDescription()));
        descCol.setPrefWidth(160);

        TableColumn<EbookSubmissionVO, String> timeCol = new TableColumn<>("提交时间");
        timeCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getCreatedTime()));
        timeCol.setPrefWidth(150);

        pendingTable.getColumns().addAll(titleCol, authorCol, publisherCol, uploaderCol, descCol, timeCol);
        pendingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        for (TableColumn<EbookSubmissionVO, ?> col : pendingTable.getColumns()) {
            col.setMinWidth(80.0);
        }
    }

    @FXML
    private void handleBack() {
        if (backAction != null) {
            backAction.run();
        }
    }

    @FXML
    private void handleRefresh() {
        refresh();
    }

    private void refresh() {
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.EBK_PENDING_LIST, null, null);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS
                            && response.getData() instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<EbookSubmissionVO> submissions = (List<EbookSubmissionVO>) response.getData();
                        pendingTable.getItems().setAll(submissions);
                    }
                });
            } catch (Exception e) {
                // 忽略查询异常
            }
        });
    }

    @FXML
    private void handleApprove() {
        review("APPROVE");
    }

    @FXML
    private void handleReject() {
        review("REJECT");
    }

    private void review(String action) {
        EbookSubmissionVO selected = pendingTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMsg("请先选择要审核的投稿", false);
            return;
        }
        String comment = commentField.getText() == null ? "" : commentField.getText().trim();
        String[] payload = new String[]{String.valueOf(selected.getId()), action, comment};

        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.EBK_REVIEW, null, payload);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS) {
                        showMsg("APPROVE".equals(action) ? "已通过并上架" : "已驳回", true);
                        commentField.clear();
                        refresh();
                    } else {
                        String errMsg = "审核失败";
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
