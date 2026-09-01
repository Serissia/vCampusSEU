package com.vcampus.client.controller;

import com.vcampus.client.net.SocketClient;
import com.vcampus.client.util.ScrollSpeedUtil;
import com.vcampus.client.util.SvgIcons;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.BookVO;
import com.vcampus.common.vo.ResourceFileVO;
import com.vcampus.common.vo.UserVO;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 图书馆图书管理控制器。
 *
 * <p>系统管理员用于维护馆藏图书信息，支持新增、编辑、删除，并录入电子资源（PDF）
 * 上传至服务端存储。</p>
 *
 * @author GGbongy
 */
public class LibraryManageController {

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
                    Thread thread = new Thread(r, "LibManage-Thread-" + threadNumber.getAndIncrement());
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
    private TextField searchField;
    @FXML
    private TableView<BookVO> bookTable;
    @FXML
    private TextField isbnField;
    @FXML
    private TextField titleField;
    @FXML
    private TextField authorField;
    @FXML
    private TextField publisherField;
    @FXML
    private TextField locationField;
    @FXML
    private TextField totalNumField;
    @FXML
    private Label resourceStatusLabel;
    @FXML
    private Label msgLabel;
    @FXML
    private Label modeLabel;
    @FXML
    private Button saveButton;

    private UserVO currentUser;
    private final SocketClient socketClient = new SocketClient();
    private Runnable backAction;

    /** 当前编辑中的图书，null 表示新增模式 */
    private BookVO editingBook;

    /** 已上传到服务端的电子资源文件名（null 表示未录入） */
    private String uploadedResourceName;

    @FXML
    private void initialize() {
        if (rootScrollPane != null) {
            ScrollSpeedUtil.applyCustomScrollSpeed(rootScrollPane);
        }
        backButton.setGraphic(SvgIcons.createIcon("arrow-left", 13, "back-icon"));
        backButton.setGraphicTextGap(6.0);
        setupTable();
        resetForm();
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
     * 构造书目表格列，并监听行选中以载入表单。
     */
    private void setupTable() {
        TableColumn<BookVO, String> isbnCol = new TableColumn<>("ISBN");
        isbnCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getIsbn()));
        isbnCol.setPrefWidth(140);

        TableColumn<BookVO, String> titleCol = new TableColumn<>("书名");
        titleCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getTitle()));
        titleCol.setPrefWidth(180);

        TableColumn<BookVO, String> authorCol = new TableColumn<>("作者");
        authorCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getAuthor()));
        authorCol.setPrefWidth(120);

        TableColumn<BookVO, String> publisherCol = new TableColumn<>("出版社");
        publisherCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getPublisher()));
        publisherCol.setPrefWidth(150);

        TableColumn<BookVO, String> locationCol = new TableColumn<>("存放位置");
        locationCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getLocation()));
        locationCol.setPrefWidth(160);

        TableColumn<BookVO, String> totalCol = new TableColumn<>("馆藏总数");
        totalCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(String.valueOf(cd.getValue().getTotalNum())));
        totalCol.setPrefWidth(80);

        TableColumn<BookVO, String> stockCol = new TableColumn<>("可借余量");
        stockCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(String.valueOf(cd.getValue().getCurrentNum())));
        stockCol.setPrefWidth(80);

        bookTable.getColumns().addAll(isbnCol, titleCol, authorCol, publisherCol, locationCol, totalCol, stockCol);
        bookTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        bookTable.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> {
            if (val != null) {
                loadBookIntoForm(val);
            }
        });
    }

    /**
     * 检索。
     */
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
     * 切换为新增模式。
     */
    @FXML
    private void handleNew() {
        editingBook = null;
        bookTable.getSelectionModel().clearSelection();
        resetForm();
        isbnField.setDisable(false);
        showMsg("请输入新书目的信息后点击「保存新增」", true);
    }

    /**
     * 保存新增或编辑。
     */
    @FXML
    private void handleSave() {
        String isbn = isbnField.getText() == null ? "" : isbnField.getText().trim();
        String title = titleField.getText() == null ? "" : titleField.getText().trim();
        String author = authorField.getText() == null ? "" : authorField.getText().trim();

        if (isbn.isEmpty() || title.isEmpty() || author.isEmpty()) {
            showMsg("ISBN、书名、作者均为必填项", false);
            return;
        }

        int totalNum;
        try {
            totalNum = Integer.parseInt(totalNumField.getText() == null ? "" : totalNumField.getText().trim());
            if (totalNum <= 0) {
                showMsg("馆藏总数必须为正整数", false);
                return;
            }
        } catch (NumberFormatException e) {
            showMsg("馆藏总数必须为正整数", false);
            return;
        }

        BookVO book = new BookVO();
        book.setIsbn(isbn);
        book.setTitle(title);
        book.setAuthor(author);
        book.setPublisher(publisherField.getText() == null ? "" : publisherField.getText().trim());
        book.setLocation(locationField.getText() == null ? "" : locationField.getText().trim());
        book.setResourceFile(uploadedResourceName);
        book.setTotalNum(totalNum);

        boolean isAdd = editingBook == null;
        MessageType type = isAdd ? MessageType.BOOK_ADD : MessageType.BOOK_UPDATE;
        if (!isAdd) {
            // 编辑时余量不在此处修改，保持原有可借余量
            book.setCurrentNum(editingBook.getCurrentNum());
        } else {
            book.setCurrentNum(totalNum);
        }

        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), type, null, book);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS) {
                        showMsg(isAdd ? "新增成功" : "保存成功", true);
                        editingBook = null;
                        resetForm();
                        isbnField.setDisable(false);
                        refreshBooks(searchField.getText() == null ? "" : searchField.getText().trim());
                    } else {
                        showMsg(isAdd ? "新增失败，ISBN 可能已存在" : "保存失败", false);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("网络错误", "无法连接服务器: " + e.getMessage(), Alert.AlertType.ERROR));
            }
        });
    }

    /**
     * 删除选中书目。
     */
    @FXML
    private void handleDelete() {
        BookVO selected = bookTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("提示", "请先在列表中选择要删除的书目", Alert.AlertType.WARNING);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认删除");
        confirm.setHeaderText(null);
        confirm.setContentText("确定删除《" + selected.getTitle() + "》吗？");
        Optional<ButtonType> result = confirm.showAndWait();
        if (!result.isPresent() || result.get() != ButtonType.OK) {
            return;
        }

        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.BOOK_DELETE, null, selected.getIsbn());
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS) {
                        showMsg("删除成功", true);
                        resetForm();
                        isbnField.setDisable(false);
                        refreshBooks(searchField.getText() == null ? "" : searchField.getText().trim());
                    } else {
                        showMsg("删除失败", false);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("网络错误", "无法连接服务器: " + e.getMessage(), Alert.AlertType.ERROR));
            }
        });
    }

    /**
     * 选择本地 PDF 文件并上传至服务端。
     */
    @FXML
    private void handleChooseResource() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择电子资源 PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF 文件", "*.pdf"));
        File file = chooser.showOpenDialog(resourceStatusLabel.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            uploadResource(data, file.getName());
        } catch (IOException e) {
            showMsg("读取文件失败：" + e.getMessage(), false);
        }
    }

    /**
     * 上传电子资源到服务端，成功后记录返回的文件名。
     */
    private void uploadResource(byte[] data, String fileName) {
        ResourceFileVO vo = new ResourceFileVO();
        vo.setFileName(fileName);
        vo.setData(data);

        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.BOOK_RESOURCE_UPLOAD, null, vo);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS
                            && response.getData() instanceof String) {
                        uploadedResourceName = (String) response.getData();
                        resourceStatusLabel.setText("已上传电子资源：" + fileName);
                        showMsg("电子资源上传成功", true);
                    } else {
                        showMsg("电子资源上传失败", false);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("网络错误", "上传失败: " + e.getMessage(), Alert.AlertType.ERROR));
            }
        });
    }

    /**
     * 清除当前录入的电子资源。
     */
    @FXML
    private void handleClearResource() {
        uploadedResourceName = null;
        resourceStatusLabel.setText("未录入电子资源");
    }

    /**
     * 将选中书目载入表单（编辑模式）。
     */
    private void loadBookIntoForm(BookVO book) {
        editingBook = book;
        isbnField.setText(book.getIsbn());
        isbnField.setDisable(true);
        titleField.setText(book.getTitle());
        authorField.setText(book.getAuthor());
        publisherField.setText(book.getPublisher());
        locationField.setText(book.getLocation());
        totalNumField.setText(String.valueOf(book.getTotalNum()));

        uploadedResourceName = book.getResourceFile();
        resourceStatusLabel.setText(uploadedResourceName == null || uploadedResourceName.trim().isEmpty()
                ? "未录入电子资源" : "已录入电子资源：" + uploadedResourceName);
        hideMsg();
        updateModeUI();
    }

    /**
     * 重置表单。
     */
    private void resetForm() {
        isbnField.clear();
        titleField.clear();
        authorField.clear();
        publisherField.clear();
        locationField.clear();
        totalNumField.clear();
        uploadedResourceName = null;
        resourceStatusLabel.setText("未录入电子资源");
        updateModeUI();
    }

    /**
     * 根据当前模式更新界面提示（新增 / 编辑）。
     */
    private void updateModeUI() {
        boolean isAdd = editingBook == null;
        if (saveButton != null) {
            saveButton.setText(isAdd ? "保存新增" : "保存修改");
        }
        if (modeLabel != null) {
            modeLabel.setText(isAdd ? "新增模式" : "编辑模式");
        }
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

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
