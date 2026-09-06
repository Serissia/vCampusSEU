package com.vcampus.client.controller;

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
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 我的电子资源投稿控制器。
 *
 * @author GGbongy
 */
public class EbookMyListController {

    private static final ExecutorService THREAD_POOL = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "EbookMyList-Thread-" + threadNumber.getAndIncrement());
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
    private TableView<EbookSubmissionVO> submissionTable;

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
        setupTable();
    }

    public void initData(UserVO user, Runnable backAction) {
        this.currentUser = user;
        this.backAction = backAction;
        refresh();
    }

    private void setupTable() {
        TableColumn<EbookSubmissionVO, String> titleCol = new TableColumn<>("书名");
        titleCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getTitle()));
        titleCol.setPrefWidth(180);

        TableColumn<EbookSubmissionVO, String> authorCol = new TableColumn<>("作者");
        authorCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getAuthor()));
        authorCol.setPrefWidth(120);

        TableColumn<EbookSubmissionVO, String> publisherCol = new TableColumn<>("出版社");
        publisherCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getPublisher()));
        publisherCol.setPrefWidth(150);

        TableColumn<EbookSubmissionVO, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getStatus()));
        statusCol.setCellFactory(col -> new TableCell<EbookSubmissionVO, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    if ("PENDING".equals(item)) {
                        setText("待审核");
                        getStyleClass().removeAll("status-borrowed", "status-overdue", "status-returned");
                        getStyleClass().add("status-borrowed");
                    } else if ("APPROVED".equals(item)) {
                        setText("已通过");
                        getStyleClass().removeAll("status-borrowed", "status-overdue", "status-returned");
                        getStyleClass().add("status-returned");
                    } else {
                        setText("已驳回");
                        getStyleClass().removeAll("status-borrowed", "status-overdue", "status-returned");
                        getStyleClass().add("status-overdue");
                    }
                }
            }
        });
        statusCol.setPrefWidth(90);

        TableColumn<EbookSubmissionVO, String> commentCol = new TableColumn<>("审核意见");
        commentCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                cd.getValue().getReviewComment() == null ? "--" : cd.getValue().getReviewComment()));
        commentCol.setPrefWidth(180);

        TableColumn<EbookSubmissionVO, String> timeCol = new TableColumn<>("提交时间");
        timeCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getCreatedTime()));
        timeCol.setPrefWidth(150);

        submissionTable.getColumns().addAll(titleCol, authorCol, publisherCol, statusCol, commentCol, timeCol);
        submissionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        for (TableColumn<EbookSubmissionVO, ?> col : submissionTable.getColumns()) {
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
                Message request = new Message(currentUser.getAccountNumber(), MessageType.EBK_MY_LIST, null, null);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS
                            && response.getData() instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<EbookSubmissionVO> submissions = (List<EbookSubmissionVO>) response.getData();
                        submissionTable.getItems().setAll(submissions);
                    }
                });
            } catch (Exception e) {
                // 忽略查询异常
            }
        });
    }
}
