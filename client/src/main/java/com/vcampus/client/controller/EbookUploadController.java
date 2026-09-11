package com.vcampus.client.controller;

import com.vcampus.client.net.ClientSession;
import com.vcampus.client.net.SocketClient;
import com.vcampus.client.util.ScrollSpeedUtil;
import com.vcampus.client.util.SvgIcons;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.EbookSubmissionVO;
import com.vcampus.common.vo.ResourceFileVO;
import com.vcampus.common.vo.UserVO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
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

/**
 * 读者上传纯电子书控制器。
 *
 * <p>读者填写书名、作者、出版社并上传 PDF，提交后进入待审核状态。</p>
 *
 * @author GGbongy
 */
public class EbookUploadController {

    private static final ExecutorService THREAD_POOL = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "EbookUpload-Thread-" + threadNumber.getAndIncrement());
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
    private TextField titleField;
    @FXML
    private TextField authorField;
    @FXML
    private TextField publisherField;
    @FXML
    private TextField descriptionField;
    @FXML
    private Label resourceStatusLabel;
    @FXML
    private Label msgLabel;

    private UserVO currentUser;
    private Runnable backAction;
    /** 全局共享连接：服务端把身份绑定在连接上，全客户端必须复用同一条 */
    private final SocketClient socketClient = ClientSession.client();

    private byte[] pendingResourceData;
    private String pendingResourceFileName;

    @FXML
    private void initialize() {
        if (rootScrollPane != null) {
            ScrollSpeedUtil.applyCustomScrollSpeed(rootScrollPane);
        }
        backButton.setGraphic(SvgIcons.createIcon("arrow-left", 13, "back-icon"));
        backButton.setGraphicTextGap(6.0);
    }

    public void initData(UserVO user, Runnable backAction) {
        this.currentUser = user;
        this.backAction = backAction;
    }

    @FXML
    private void handleBack() {
        if (backAction != null) {
            backAction.run();
        }
    }

    @FXML
    private void handleChooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择电子资源 PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF 文件", "*.pdf"));
        File file = chooser.showOpenDialog(resourceStatusLabel.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            pendingResourceData = Files.readAllBytes(file.toPath());
            pendingResourceFileName = file.getName();
            resourceStatusLabel.setText("已选择：" + file.getName());
        } catch (IOException e) {
            showMsg("读取文件失败：" + e.getMessage(), false);
        }
    }

    @FXML
    private void handleSubmit() {
        String title = titleField.getText() == null ? "" : titleField.getText().trim();
        String author = authorField.getText() == null ? "" : authorField.getText().trim();
        String publisher = publisherField.getText() == null ? "" : publisherField.getText().trim();
        String description = descriptionField.getText() == null ? "" : descriptionField.getText().trim();

        if (title.isEmpty() || author.isEmpty() || publisher.isEmpty()) {
            showMsg("书名、作者、出版社均为必填项", false);
            return;
        }
        if (pendingResourceData == null) {
            showMsg("请先选择要上传的 PDF 文件", false);
            return;
        }

        byte[] data = pendingResourceData;
        String fileName = pendingResourceFileName;
        EbookSubmissionVO submission = new EbookSubmissionVO();
        submission.setTitle(title);
        submission.setAuthor(author);
        submission.setPublisher(publisher);
        submission.setDescription(description.isEmpty() ? null : description);

        THREAD_POOL.execute(() -> {
            try {
                // 先上传 PDF 拿到资源文件名
                ResourceFileVO vo = new ResourceFileVO();
                vo.setFileName(fileName);
                vo.setData(data);
                Message uploadReq = new Message(currentUser.getAccountNumber(), MessageType.BOOK_RESOURCE_UPLOAD, null, vo);
                Message uploadResp = socketClient.send(uploadReq);
                if (uploadResp == null || uploadResp.getCode() != ResponseCode.SUCCESS
                        || !(uploadResp.getData() instanceof String)) {
                    Platform.runLater(() -> showMsg("电子资源上传失败", false));
                    return;
                }
                submission.setResourceFile((String) uploadResp.getData());

                // 提交投稿
                Message submitReq = new Message(currentUser.getAccountNumber(), MessageType.EBK_SUBMIT, null, submission);
                Message submitResp = socketClient.send(submitReq);
                Platform.runLater(() -> {
                    if (submitResp != null && submitResp.getCode() == ResponseCode.SUCCESS) {
                        showMsg("提交成功，等待管理员审核", true);
                        titleField.clear();
                        authorField.clear();
                        publisherField.clear();
                        descriptionField.clear();
                        pendingResourceData = null;
                        pendingResourceFileName = null;
                        resourceStatusLabel.setText("未选择文件");
                    } else {
                        showMsg("提交失败，请稍后重试", false);
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
