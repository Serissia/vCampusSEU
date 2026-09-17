package com.vcampus.client.controller;

import com.vcampus.client.net.ClientSession;
import com.vcampus.client.net.SocketClient;
import com.vcampus.client.util.SvgIcons;
import com.vcampus.client.util.ToastBannerUtil;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.BookVO;
import com.vcampus.common.vo.ResourceFileVO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * 图书详细信息页控制器。
 *
 * <p>展示图书完整信息，并提供在线浏览与电子资源下载入口。</p>
 *
 * @author GGbongy
 */
public class BookDetailController {

    private static final ExecutorService THREAD_POOL = new ThreadPoolExecutor(
            1,
            2,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(20),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "BookDetail-Download-" + threadNumber.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @FXML
    private Label detailTitleText;
    @FXML
    private Label isbnValueText;
    @FXML
    private Label authorValueText;
    @FXML
    private Label publisherValueText;
    @FXML
    private Label categoryValueText;
    @FXML
    private Label locationValueText;
    @FXML
    private Label totalValueText;
    @FXML
    private Label currentValueText;
    @FXML
    private Label statusValueText;
    @FXML
    private Button onlineReadButton;
    @FXML
    private Button downloadButton;
    @FXML
    private ProgressIndicator downloadProgress;
    @FXML
    private ScrollPane rootScrollPane;
    @FXML
    private Button backButton;

    private BookVO book;
    /** 全局共享连接：下载电子资源时与服务端保持同一条长连接 */
    private final SocketClient socketClient = ClientSession.client();
    private Runnable backAction;
    private BiConsumer<String, String> browseAction;

    @FXML
    private void initialize() {
        backButton.setGraphic(SvgIcons.createIcon("arrow-left", 13, "back-icon"));
        backButton.setGraphicTextGap(6.0);
    }

    /**
     * 注入图书数据与导航回调。
     *
     * @param book         图书实体
     * @param backAction   返回上一层动作
     * @param browseAction 跳转在线浏览动作（参数为 resourceFile、书名）
     */
    public void initData(BookVO book, Runnable backAction, BiConsumer<String, String> browseAction) {
        this.book = book;
        this.backAction = backAction;
        this.browseAction = browseAction;
        if (book == null) {
            return;
        }

        detailTitleText.setText(book.getTitle());
        isbnValueText.setText(book.getIsbn());
        authorValueText.setText(book.getAuthor());
        publisherValueText.setText(book.getPublisher() == null || book.getPublisher().trim().isEmpty()
                ? "--" : book.getPublisher());
        categoryValueText.setText(book.getCategory() == null || book.getCategory().trim().isEmpty()
                ? "未分类" : book.getCategory());
        locationValueText.setText(book.getLocation() == null || book.getLocation().trim().isEmpty()
                ? "--" : book.getLocation());
        totalValueText.setText(String.valueOf(book.getTotalNum()));
        currentValueText.setText(String.valueOf(book.getCurrentNum()));

        boolean available = book.getCurrentNum() > 0;
        statusValueText.setText(available ? "可借" : "已借完");
        statusValueText.getStyleClass().removeAll("status-borrowed", "status-unavailable");
        statusValueText.getStyleClass().add(available ? "status-borrowed" : "status-unavailable");

        boolean hasOnline = book.getResourceFile() != null && !book.getResourceFile().trim().isEmpty();
        if (hasOnline) {
            onlineReadButton.setText("在线浏览");
            onlineReadButton.setDisable(false);
            downloadButton.setDisable(false);
            downloadProgress.setVisible(false);
            downloadProgress.setManaged(false);
        } else {
            onlineReadButton.setText("暂无在线资源");
            onlineReadButton.setDisable(true);
            downloadButton.setDisable(true);
            downloadProgress.setVisible(false);
            downloadProgress.setManaged(false);
        }
    }

    /**
     * 返回上一层。
     */
    @FXML
    private void handleBack() {
        if (backAction != null) {
            backAction.run();
        }
    }

    /**
     * 跳转在线浏览阅读器。
     */
    @FXML
    private void handleOnlineRead() {
        if (book != null && book.getResourceFile() != null && !book.getResourceFile().trim().isEmpty()
                && browseAction != null) {
            browseAction.accept(book.getResourceFile().trim(), book.getTitle());
        }
    }

    /**
     * 下载当前图书的电子资源到用户选择的本地文件。
     */
    @FXML
    private void handleDownload() {
        if (book == null || book.getResourceFile() == null || book.getResourceFile().trim().isEmpty()) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("保存电子图书资源");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF 文件", "*.pdf"));
        chooser.setInitialFileName(book.getResourceFile().trim());
        File target = chooser.showSaveDialog(downloadButton.getScene().getWindow());
        if (target == null) {
            return;
        }

        downloadButton.setDisable(true);
        downloadProgress.setVisible(true);
        downloadProgress.setManaged(true);
        ToastBannerUtil.showToastBanner(rootScrollPane, "开始下载电子资源...", 2);

        String resourceName = book.getResourceFile().trim();
        THREAD_POOL.execute(() -> {
            try {
                String uid = ClientSession.getInstance().getCurrentUser() == null
                        ? "" : ClientSession.getInstance().getCurrentUser().getAccountNumber();
                Message request = new Message(uid, MessageType.BOOK_RESOURCE_DOWNLOAD, null, resourceName);
                Message response = socketClient.send(request);
                if (response == null || response.getCode() != ResponseCode.SUCCESS
                        || !(response.getData() instanceof ResourceFileVO)) {
                    Platform.runLater(() -> {
                        resetDownloadUi();
                        ToastBannerUtil.showToastBanner(rootScrollPane, "下载失败：服务器未返回有效的电子资源", 1);
                    });
                    return;
                }

                ResourceFileVO file = (ResourceFileVO) response.getData();
                Files.write(target.toPath(), file.getData());
                Platform.runLater(() -> {
                    resetDownloadUi();
                    ToastBannerUtil.showToastBanner(rootScrollPane, "下载完成", 0);
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    resetDownloadUi();
                    ToastBannerUtil.showToastBanner(rootScrollPane, "下载失败：" + e.getMessage(), 1);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    resetDownloadUi();
                    ToastBannerUtil.showToastBanner(rootScrollPane, "下载失败：" + e.getMessage(), 1);
                });
            }
        });
    }

    /**
     * 下载结束后恢复下载按钮并隐藏进度指示器。
     */
    private void resetDownloadUi() {
        boolean hasOnline = book != null && book.getResourceFile() != null
                && !book.getResourceFile().trim().isEmpty();
        downloadButton.setDisable(!hasOnline);
        downloadProgress.setVisible(false);
        downloadProgress.setManaged(false);
    }
}
