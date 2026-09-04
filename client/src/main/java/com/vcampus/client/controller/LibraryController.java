package com.vcampus.client.controller;

import com.vcampus.client.net.SocketClient;
import com.vcampus.client.util.ScrollSpeedUtil;
import com.vcampus.client.util.SvgIcons;
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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
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
        if (currentUser != null
                && (currentUser.getRole() == UserRole.ADMIN || currentUser.getRole() == UserRole.LIBRARIAN)) {
            // 管理员 / 图书管理员：进入虚拟图书馆后先展示业务选择中心
            libraryRoot.getChildren().setAll(buildAdminHub());
            return;
        }

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
        for (TableColumn<BookVO, ?> col : bookTable.getColumns()) {
            col.setMinWidth(80.0);
        }
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
                BorrowRecordVO record = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || record == null) {
                    setText(null);
                } else if ("BORROWED".equals(record.getStatus()) && record.getOverdueDays() > 0) {
                    setText("已逾期 " + record.getOverdueDays() + " 天");
                    getStyleClass().removeAll("status-borrowed", "status-returned", "status-overdue");
                    getStyleClass().add("status-overdue");
                } else if ("BORROWED".equals(record.getStatus())) {
                    setText("借阅中");
                    getStyleClass().removeAll("status-borrowed", "status-returned", "status-overdue");
                    getStyleClass().add("status-borrowed");
                } else {
                    setText("已归还");
                    getStyleClass().removeAll("status-borrowed", "status-returned", "status-overdue");
                    getStyleClass().add("status-returned");
                }
            }
        });
        statusCol.setPrefWidth(110);

        TableColumn<BorrowRecordVO, String> renewCol = new TableColumn<>("操作");
        renewCol.setCellFactory(col -> new TableCell<BorrowRecordVO, String>() {
            private final Button btn = new Button("续借");

            {
                btn.getStyleClass().add("lib-btn-borrow");
                btn.setOnAction(e -> {
                    BorrowRecordVO record = getTableRow().getItem();
                    if (record != null) {
                        renewBook(record);
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                BorrowRecordVO record = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || record == null) {
                    setGraphic(null);
                } else {
                    boolean canRenew = "BORROWED".equals(record.getStatus())
                            && record.getOverdueDays() == 0
                            && record.getRenewCount() < 1;
                    btn.setDisable(!canRenew);
                    setGraphic(btn);
                }
            }
        });
        renewCol.setPrefWidth(80);

        borrowTable.getColumns().addAll(titleCol, isbnCol, borrowCol, dueCol, returnCol, statusCol, renewCol);
        borrowTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        for (TableColumn<BorrowRecordVO, ?> col : borrowTable.getColumns()) {
            col.setMinWidth(80.0);
        }
    }

    /**
     * 学生自助续借。
     */
    private void renewBook(BorrowRecordVO record) {
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.BOOK_RENEW, null, record.getIsbn());
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS) {
                        showAlert("续借成功", "《" + record.getTitle() + "》续借成功，应还日期已顺延 30 天。",
                                Alert.AlertType.INFORMATION);
                        refreshMyBorrows();
                    } else {
                        showAlert("续借失败", resolveRenewError(response), Alert.AlertType.WARNING);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("网络错误", "无法连接服务器: " + e.getMessage(), Alert.AlertType.ERROR));
            }
        });
    }

    /**
     * 将续借状态码转换为可读提示。
     */
    private String resolveRenewError(Message response) {
        if (response == null) {
            return "请求被服务器拒绝";
        }
        switch (response.getCode()) {
            case NOT_BORROWED:
                return "您当前未借阅该书";
            case RENEW_LIMIT_EXCEEDED:
                return "该书已续借过一次，不能再次续借";
            case OVERDUE:
                return "该书已逾期，请先归还";
            default:
                return "续借失败，请稍后重试";
        }
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
     * 构建管理员业务选择中心（唯一入口内的三级导航）。
     */
    private Node buildAdminHub() {
        VBox container = new VBox(16.0);
        container.setPadding(new Insets(10.0));
        container.getStyleClass().add("lib-container");

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("profile-card");
        VBox headerText = new VBox(4.0);
        Label title = new Label("虚拟图书馆");
        title.getStyleClass().add("lib-title");
        Label subtitle = new Label("请选择需要办理的业务");
        subtitle.getStyleClass().add("lib-subtitle");
        headerText.getChildren().addAll(title, subtitle);
        header.getChildren().add(headerText);

        HBox cards = new HBox(16.0);
        cards.getChildren().addAll(
                createHubCard("办理借阅", "为读者办理图书借出", "borrow-in", this::showBorrowProcess),
                createHubCard("办理归还", "为读者办理图书归还", "borrow-out", this::showReturnProcess),
                createHubCard("图书管理", "维护馆藏图书与电子资源", "library-manage", this::showLibraryManage));

        container.getChildren().addAll(header, cards);
        return container;
    }

    /**
     * 创建业务选择卡片。
     */
    private Node createHubCard(String title, String desc, String iconKey, Runnable action) {
        VBox card = new VBox(10.0);
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(200.0);
        card.setMinHeight(150.0);
        card.getStyleClass().add("lib-hub-card");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("lib-hub-title");

        Label descLabel = new Label(desc);
        descLabel.getStyleClass().add("lib-subtitle");
        descLabel.setWrapText(true);

        card.getChildren().addAll(SvgIcons.createIcon(iconKey, 28.0, "lib-hub-icon"), titleLabel, descLabel);
        card.setCursor(Cursor.HAND);
        card.setOnMouseClicked(e -> action.run());
        return card;
    }

    private void showBorrowProcess() {
        loadSubView("/fxml/BorrowProcessView.fxml");
    }

    private void showReturnProcess() {
        loadSubView("/fxml/ReturnProcessView.fxml");
    }

    private void showLibraryManage() {
        loadSubView("/fxml/LibraryManageView.fxml");
    }

    /**
     * 加载管理员子视图并注入返回回调，随后导航进入。
     */
    private void loadSubView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node root = loader.load();
            Object controller = loader.getController();
            if (controller instanceof BorrowProcessController) {
                ((BorrowProcessController) controller).initData(currentUser, this::navigateBack);
            } else if (controller instanceof ReturnProcessController) {
                ((ReturnProcessController) controller).initData(currentUser, this::navigateBack);
            } else if (controller instanceof LibraryManageController) {
                ((LibraryManageController) controller).initData(currentUser, this::navigateBack);
            }
            navigateTo(root);
        } catch (IOException e) {
            e.printStackTrace();
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
