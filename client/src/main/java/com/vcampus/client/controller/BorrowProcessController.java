package com.vcampus.client.controller;

import com.vcampus.client.net.SocketClient;
import com.vcampus.client.util.ScrollSpeedUtil;
import com.vcampus.client.util.SvgIcons;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.BookVO;
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
 * 图书办理借阅控制器。
 *
 * <p>系统管理员输入借阅人学号 / 工号后，检索图书并为借阅人办理借出。</p>
 *
 * @author GGbongy
 */
public class BorrowProcessController {

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
                    Thread thread = new Thread(r, "BorrowProcess-Thread-" + threadNumber.getAndIncrement());
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
    private TextField searchField;
    @FXML
    private TableView<BookVO> bookTable;
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
        refreshBooks(searchField.getText() == null ? "" : searchField.getText().trim());
    }

    @FXML
    private void handleBack() {
        if (backAction != null) {
            backAction.run();
        }
    }

    /**
     * 构造书目表格列，并附借出操作按钮。
     */
    private void setupTable() {
        TableColumn<BookVO, String> isbnCol = new TableColumn<>("ISBN");
        isbnCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getIsbn()));
        isbnCol.setPrefWidth(130);

        TableColumn<BookVO, String> titleCol = new TableColumn<>("书名");
        titleCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getTitle()));
        titleCol.setPrefWidth(170);

        TableColumn<BookVO, String> authorCol = new TableColumn<>("作者");
        authorCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getAuthor()));
        authorCol.setPrefWidth(120);

        TableColumn<BookVO, String> publisherCol = new TableColumn<>("出版社");
        publisherCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getPublisher()));
        publisherCol.setPrefWidth(150);

        TableColumn<BookVO, String> locationCol = new TableColumn<>("存放位置");
        locationCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getLocation()));
        locationCol.setPrefWidth(150);

        TableColumn<BookVO, String> stockCol = new TableColumn<>("馆藏/可借");
        stockCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                cd.getValue().getTotalNum() + " / " + cd.getValue().getCurrentNum()));
        stockCol.setPrefWidth(90);

        TableColumn<BookVO, String> actionCol = new TableColumn<>("操作");
        actionCol.setCellFactory(col -> new TableCell<BookVO, String>() {
            private final Button btn = new Button("借出");

            {
                btn.getStyleClass().add("lib-btn-borrow");
                btn.setOnAction(e -> {
                    BookVO book = getTableRow().getItem();
                    if (book != null) {
                        borrowBook(book);
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                BookVO book = getTableRow().getItem();
                if (empty || book == null) {
                    setGraphic(null);
                } else {
                    btn.setDisable(book.getCurrentNum() <= 0);
                    setGraphic(btn);
                }
            }
        });
        actionCol.setPrefWidth(80);

        bookTable.getColumns().addAll(isbnCol, titleCol, authorCol, publisherCol, locationCol, stockCol, actionCol);
        bookTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        for (TableColumn<BookVO, ?> col : bookTable.getColumns()) {
            col.setMinWidth(80.0);
        }
    }

    @FXML
    private void handleSearch() {
        String keyword = searchField.getText() == null ? "" : searchField.getText().trim();
        refreshBooks(keyword);
    }

    private void refreshBooks(String keyword) {
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.BOOK_QUERY, null, keyword);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS
                            && response.getData() instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<BookVO> books = (List<BookVO>) response.getData();
                        bookTable.getItems().setAll(books);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("网络错误", "无法连接服务器: " + e.getMessage(), Alert.AlertType.ERROR));
            }
        });
    }

    /**
     * 为指定借阅人办理借出。
     */
    private void borrowBook(BookVO book) {
        String studentId = studentIdField.getText() == null ? "" : studentIdField.getText().trim();
        if (studentId.isEmpty()) {
            showMsg("请先输入借阅人学号 / 工号", false);
            return;
        }

        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.BOOK_BORROW, null,
                        new String[]{studentId, book.getIsbn()});
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS) {
                        showMsg("《" + book.getTitle() + "》已借出给 " + studentId, true);
                        refreshBooks(searchField.getText() == null ? "" : searchField.getText().trim());
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
            case BOOK_NOT_FOUND:
                return "该书目不存在或已被删除";
            case BOOK_NO_STOCK:
                return "该书当前无可借余量";
            case BORROW_LIMIT_EXCEEDED:
                return "该借阅人已达同时借阅上限（5 本）";
            case ALREADY_BORROWED:
                return "该借阅人已借阅此书且尚未归还";
            case INVALID_REQUEST:
                return "请求参数不合法";
            default:
                return "借出失败，请核对借阅人学号是否正确";
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
