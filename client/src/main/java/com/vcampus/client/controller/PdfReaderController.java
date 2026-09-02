package com.vcampus.client.controller;

import com.vcampus.client.net.SocketClient;
import com.vcampus.client.util.ScrollSpeedUtil;
import com.vcampus.client.util.SvgIcons;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.ResourceFileVO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 在线浏览 PDF 阅读器控制器。
 *
 * <p>从服务端下载电子资源字节流，使用 PDFBox 逐页渲染并在应用内展示，支持翻页；
 * 返回动作由父级（LibraryController）通过回调处理。</p>
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

    private static final float RENDER_DPI = 120f;

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

    private String uid;
    private PDDocument document;
    private int currentPage = 0;
    private int pageCount = 0;
    private Runnable backAction;

    @FXML
    private void initialize() {
        backButton.setGraphic(SvgIcons.createIcon("arrow-left", 13, "back-icon"));
        backButton.setGraphicTextGap(6.0);
        // 页面宽度自适应阅读区
        pdfImageView.fitWidthProperty().bind(pdfScroll.widthProperty().subtract(40));
        // 顺滑滚轮滚动
        ScrollSpeedUtil.applyCustomScrollSpeed(pdfScroll);
    }

    /**
     * 注入资源标识、标题、当前用户与返回回调。
     */
    public void initData(String resourceName, String title, String uid, Runnable backAction) {
        this.uid = uid;
        this.backAction = backAction;
        readerTitleText.setText(title == null || title.trim().isEmpty() ? "在线浏览" : title);
        pageIndicatorLabel.setText("正在加载…");
        prevButton.setDisable(true);
        nextButton.setDisable(true);
        downloadAndLoad(resourceName);
    }

    /**
     * 从服务端下载电子资源并加载 PDF。
     */
    private void downloadAndLoad(String resourceName) {
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(uid, MessageType.BOOK_RESOURCE_DOWNLOAD, null, resourceName);
                Message response = socketClient.send(request);
                if (response == null || response.getCode() != ResponseCode.SUCCESS
                        || !(response.getData() instanceof ResourceFileVO)) {
                    Platform.runLater(() -> pageIndicatorLabel.setText("加载失败"));
                    return;
                }
                ResourceFileVO file = (ResourceFileVO) response.getData();
                PDDocument doc = PDDocument.load(file.getData());
                document = doc;
                pageCount = doc.getNumberOfPages();
                if (pageCount <= 0) {
                    Platform.runLater(() -> pageIndicatorLabel.setText("无有效页面"));
                    return;
                }
                renderPage(0);
            } catch (Exception e) {
                Platform.runLater(() -> pageIndicatorLabel.setText("加载失败"));
            }
        });
    }

    /**
     * 异步渲染指定页。
     */
    private void renderPage(int index) {
        if (document == null || index < 0 || index >= pageCount) {
            return;
        }
        THREAD_POOL.execute(() -> {
            try {
                PDFRenderer renderer = new PDFRenderer(document);
                BufferedImage buffered = renderer.renderImageWithDPI(index, RENDER_DPI);
                Image image = toImage(buffered);
                Platform.runLater(() -> {
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
        closeDocument();
        if (backAction != null) {
            backAction.run();
        }
    }

    /**
     * 关闭并释放 PDF 文档资源。
     */
    private void closeDocument() {
        if (document != null) {
            try {
                document.close();
            } catch (Exception ignored) {
                // 忽略关闭异常
            }
            document = null;
        }
    }

    /**
     * 将 BufferedImage 转换为 JavaFX Image。
     */
    private static Image toImage(BufferedImage buffered) {
        int width = buffered.getWidth();
        int height = buffered.getHeight();
        WritableImage writable = new WritableImage(width, height);
        PixelWriter writer = writable.getPixelWriter();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                writer.setArgb(x, y, buffered.getRGB(x, y));
            }
        }
        return writable;
    }
}
