package com.vcampus.client.controller;

import com.vcampus.client.net.SocketClient;
import com.vcampus.client.util.ScrollSpeedUtil;
import com.vcampus.client.util.SvgIcons;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.PdfPageRequestVO;
import com.vcampus.common.vo.ResourceFileVO;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 在线浏览 PDF 阅读器控制器（服务端渲染）。
 *
 * <p>整份 PDF 仅在服务端存储与渲染，客户端按需拉取「当前页」的 PNG 图片，
 * 全程不在客户端落盘、也不持有整份 PDF 字节，翻页即请求服务端渲染对应页。</p>
 *
 * @author GGbongy
 */
public class PdfReaderController {

    private static final ExecutorService THREAD_POOL = new ThreadPoolExecutor(
            2,
            4,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(50),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "PdfReader-Thread-" + threadNumber.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @FXML
    private ScrollPane pdfScroll;
    @FXML
    private Label readerTitleText;
    @FXML
    private Label pageIndicatorLabel;
    @FXML
    private Button backButton;
    @FXML
    private Button prevButton;
    @FXML
    private Button nextButton;
    @FXML
    private ImageView pdfImageView;

    private final SocketClient socketClient = new SocketClient();

    private String resourceName;
    private String uid;
    private int currentPage = 0;
    private int pageCount = 0;
    private Runnable backAction;
    private boolean closing = false;

    /** 窗口宽度变化后的防抖，避免缩放过程中频繁请求渲染 */
    private final PauseTransition resizeDebounce = new PauseTransition(Duration.millis(300));

    @FXML
    private void initialize() {
        backButton.setGraphic(SvgIcons.createIcon("arrow-left", 13, "back-icon"));
        backButton.setGraphicTextGap(6.0);
        // 页面宽度自适应阅读区
        pdfImageView.fitWidthProperty().bind(pdfScroll.widthProperty().subtract(40));
        // 顺滑滚轮滚动
        ScrollSpeedUtil.applyCustomScrollSpeed(pdfScroll);
        // 宽度变化时（缩放窗口）防抖后重渲当前页
        resizeDebounce.setOnFinished(e -> renderPage(currentPage));
        pdfScroll.widthProperty().addListener((obs, o, n) -> {
            if (!closing && pageCount > 0 && pdfImageView.getImage() != null) {
                resizeDebounce.playFromStart();
            }
        });
    }

    /**
     * 注入资源标识、标题、当前用户与返回回调。
     */
    public void initData(String resourceName, String title, String uid, Runnable backAction) {
        this.resourceName = resourceName;
        this.uid = uid;
        this.backAction = backAction;
        readerTitleText.setText(title == null || title.trim().isEmpty() ? "在线浏览" : title);
        pageIndicatorLabel.setText("正在加载…");
        prevButton.setDisable(true);
        nextButton.setDisable(true);
        fetchPageCount();
    }

    /**
     * 查询电子资源总页数，成功后渲染第一页。
     */
    private void fetchPageCount() {
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(uid, MessageType.BOOK_RESOURCE_PAGE_COUNT, null, resourceName);
                Message response = socketClient.send(request);
                if (response == null || response.getCode() != ResponseCode.SUCCESS
                        || !(response.getData() instanceof Integer)) {
                    Platform.runLater(() -> pageIndicatorLabel.setText("加载失败"));
                    return;
                }
                int count = (Integer) response.getData();
                Platform.runLater(() -> {
                    if (closing) {
                        return;
                    }
                    pageCount = count;
                    if (pageCount <= 0) {
                        pageIndicatorLabel.setText("无有效页面");
                        return;
                    }
                    renderPage(0);
                });
            } catch (Exception e) {
                Platform.runLater(() -> pageIndicatorLabel.setText("加载失败"));
            }
        });
    }

    /**
     * 请求服务端渲染指定页并展示。
     */
    private void renderPage(int index) {
        if (index < 0 || index >= pageCount) {
            return;
        }
        // 在 FX 线程读取目标宽度（JavaFX 节点属性不可跨线程访问）
        final int width = (int) Math.max(200, pdfScroll.getWidth() - 40);
        pdfImageView.setImage(null); // 释放上一页图片，避免随翻页累积
        pageIndicatorLabel.setText("加载中…");
        THREAD_POOL.execute(() -> {
            try {
                PdfPageRequestVO req = new PdfPageRequestVO(resourceName, index, width);
                Message request = new Message(uid, MessageType.BOOK_RESOURCE_RENDER_PAGE, null, req);
                Message response = socketClient.send(request);
                if (response == null || response.getCode() != ResponseCode.SUCCESS
                        || !(response.getData() instanceof ResourceFileVO)) {
                    Platform.runLater(() -> pageIndicatorLabel.setText("渲染失败"));
                    return;
                }
                ResourceFileVO file = (ResourceFileVO) response.getData();
                byte[] data = file.getData();
                if (data == null || data.length == 0) {
                    Platform.runLater(() -> pageIndicatorLabel.setText("渲染失败"));
                    return;
                }
                Image image = new Image(new ByteArrayInputStream(data));
                Platform.runLater(() -> {
                    if (closing) {
                        return;
                    }
                    pdfImageView.setImage(image);
                    currentPage = index;
                    pageIndicatorLabel.setText("第 " + (index + 1) + " / " + pageCount + " 页");
                    prevButton.setDisable(index <= 0);
                    nextButton.setDisable(index >= pageCount - 1);
                    // 翻页后回到新一页开头
                    pdfScroll.setVvalue(0.0);
                    pdfScroll.setHvalue(0.0);
                });
            } catch (Exception e) {
                Platform.runLater(() -> pageIndicatorLabel.setText("渲染失败"));
            }
        });
    }

    @FXML
    private void handlePrevPage() {
        if (currentPage > 0) {
            renderPage(currentPage - 1);
        }
    }

    @FXML
    private void handleNextPage() {
        if (currentPage < pageCount - 1) {
            renderPage(currentPage + 1);
        }
    }

    @FXML
    private void handleBack() {
        closing = true;
        pdfImageView.setImage(null);
        if (backAction != null) {
            backAction.run();
        }
    }
}
