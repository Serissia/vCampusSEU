package com.vcampus.client.controller;

import com.vcampus.client.net.SocketClient;
import com.vcampus.client.util.ScrollSpeedUtil;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.BookVO;
import com.vcampus.common.vo.BorrowRecordVO;
import com.vcampus.common.vo.UserRole;
import com.vcampus.common.vo.UserVO;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 图书馆查询模块控制器，兼作图书馆模块的导航宿主。
 *
 * <p>学生与教师仅可检索馆藏、查看个人借阅记录；双击书目条目进入详情页，
 * 详情页可跳转在线浏览 PDF 阅读器。详情页与阅读器均以主内容区堆叠方式展示，
 * 通过返回按钮逐层退回。</p>
 *
 * @author GGbongy
 */
public class LibraryController {

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
                    Thread thread = new Thread(r, "Library-Thread-" + threadNumber.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @FXML
    private StackPane libraryRoot;
    @FXML
    private ScrollPane rootScrollPane;
    @FXML
    private Label libSubtitleText;
    @FXML
    private TextField searchField;
    @FXML
    private TableView<BookVO> bookTable;
    @FXML
    private TableView<BorrowRecordVO> borrowTable;
    @FXML
    private VBox myBorrowSection;

    private UserVO currentUser;
    private final SocketClient socketClient = new SocketClient();

    /** 导航历史栈，用于返回时恢复上一层视图 */
    private final Deque<Node> navStack = new ArrayDeque<>();

    @FXML
    private void initialize() {
        if (rootScrollPane != null) {
            ScrollSpeedUtil.applyCustomScrollSpeed(rootScrollPane);
        }
        setupBookTable();
        setupBorrowTable();
        // 双击书目条目进入详情页
        bookTable.setRowFactory(tv -> {
            TableRow<BookVO> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    showBookDetail(row.getItem());
                }
            });
            return row;
        });
    }

    /**
     * 注入用户上下文：学生与教师视为普通用户，展示个人借阅记录。
     */
    public void initData(UserVO user) {
        this.currentUser = user;
        boolean isRegularUser = currentUser != null
                && (currentUser.getRole() == UserRole.STUDENT || currentUser.getRole() == UserRole.TEACHER);

        if (myBorrowSection != null) {
            myBorrowSection.setVisible(isRegularUser);
            myBorrowSection.setManaged(isRegularUser);
        }
        if (libSubtitleText != null) {
            libSubtitleText.setText(isRegularUser
                    ? "检索馆藏图书，双击条目查看详情"
                    : "检索馆藏图书与存放位置信息");
        }

        refreshBooks(searchField.getText() == null ? "" : searchField.getText().trim());
        if (isRegularUser) {
            refreshMyBorrows();
        }
    }

    /**
     * 构造馆藏书目表格列。
     */
    private void setupBookTable() {
        TableColumn<BookVO, String> isbnCol = new TableColumn<>("ISBN");
        isbnCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getIsbn()));
        isbnCol.setPrefWidth(140);

        TableColumn<BookVO, String> titleCol = new TableColumn<>("书名");
        titleCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getTitle()));
        titleCol.setPrefWidth(180);

        TableColumn<BookVO, String> authorCol = new TableColumn<>("作者");
        authorCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getAuthor()));
        authorCol.setPrefWidth(140);

        TableColumn<BookVO, String> publisherCol = new TableColumn<>("出版社");
        publisherCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getPublisher()));
        publisherCol.setPrefWidth(150);

        TableColumn<BookVO, String> locationCol = new TableColumn<>("存放位置");
        locationCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getLocation()));
        locationCol.setPrefWidth(160);

        TableColumn<BookVO, String> stockCol = new TableColumn<>("馆藏/可借");
        stockCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                cd.getValue().getTotalNum() + " / " + cd.getValue().getCurrentNum()));
        stockCol.setPrefWidth(90);

        bookTable.getColumns().addAll(isbnCol, titleCol, authorCol, publisherCol, locationCol, stockCol);
        bookTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    /**
     * 构造我的借阅表格列（只读）。
     */
    private void setupBorrowTable() {
        TableColumn<BorrowRecordVO, String> titleCol = new TableColumn<>("书名");
        titleCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getTitle()));
        titleCol.setPrefWidth(200);

        TableColumn<BorrowRecordVO, String> isbnCol = new TableColumn<>("ISBN");
        isbnCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getIsbn()));
        isbnCol.setPrefWidth(140);

        TableColumn<BorrowRecordVO, String> borrowCol = new TableColumn<>("借出日期");
        borrowCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getBorrowDate()));
        borrowCol.setPrefWidth(110);

        TableColumn<BorrowRecordVO, String> dueCol = new TableColumn<>("应还日期");
        dueCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getDueDate()));
        dueCol.setPrefWidth(110);

        TableColumn<BorrowRecordVO, String> returnCol = new TableColumn<>("归还日期");
        returnCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                cd.getValue().getReturnDate() == null ? "--" : cd.getValue().getReturnDate()));
        returnCol.setPrefWidth(110);

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

        borrowTable.getColumns().addAll(titleCol, isbnCol, borrowCol, dueCol, returnCol, statusCol);
        borrowTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    /**
     * 触发关键字检索。
     */
    @FXML
    private void handleSearch() {
        String keyword = searchField.getText() == null ? "" : searchField.getText().trim();
        refreshBooks(keyword);
    }

    /**
     * 异步检索图书。
     */
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
     * 异步刷新个人借阅记录。
     */
    private void refreshMyBorrows() {
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.BORROW_MY_LIST, null, null);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS
                            && response.getData() instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<BorrowRecordVO> records = (List<BorrowRecordVO>) response.getData();
                        borrowTable.getItems().setAll(records);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("网络错误", "无法连接服务器: " + e.getMessage(), Alert.AlertType.ERROR));
            }
        });
    }

    /**
     * 在主内容区推入图书详情页。
     */
    private void showBookDetail(BookVO book) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/BookDetailView.fxml"));
            Node root = loader.load();
            BookDetailController controller = loader.getController();
            controller.initData(book, this::navigateBack, this::showPdfReader);
            navigateTo(root);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 在主内容区推入在线浏览 PDF 阅读器。
     */
    private void showPdfReader(String resourceName, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PdfReaderView.fxml"));
            Node root = loader.load();
            PdfReaderController controller = loader.getController();
            controller.initData(resourceName, title, currentUser.getAccountNumber(), this::navigateBack);
            navigateTo(root);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 显示指定视图，替换当前内容（不做叠加）。
     */
    private void showView(Node node) {
        libraryRoot.getChildren().setAll(node);
    }

    /**
     * 前进导航：将当前视图压入历史栈后，显示新视图。
     */
    private void navigateTo(Node node) {
        if (!libraryRoot.getChildren().isEmpty()) {
            navStack.push(libraryRoot.getChildren().get(0));
        }
        showView(node);
    }

    /**
     * 后退导航：从历史栈弹出上一层视图并显示。
     */
    private void navigateBack() {
        if (!navStack.isEmpty()) {
            showView(navStack.pop());
        }
    }

    /**
     * 显示提示弹窗。
     */
    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
