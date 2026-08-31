package com.vcampus.client.controller;

import com.vcampus.client.util.SvgIcons;
import com.vcampus.common.vo.BookVO;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import java.util.function.BiConsumer;

/**
 * 图书详细信息页控制器。
 *
 * <p>展示图书完整信息，并提供在线浏览入口：无在线资源时按钮置灰并显示「暂无在线资源」。
 * 返回与在线浏览动作由父级（LibraryController）通过回调处理。</p>
 *
 * @author GGbongy
 */
public class BookDetailController {

    @FXML
    private Label detailTitleText;
    @FXML
    private Label isbnValueText;
    @FXML
    private Label authorValueText;
    @FXML
    private Label publisherValueText;
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
    private Button backButton;

    private BookVO book;
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
     * @param browseAction 跳转在线浏览动作（参数为 url、书名）
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
        locationValueText.setText(book.getLocation() == null || book.getLocation().trim().isEmpty()
                ? "--" : book.getLocation());
        totalValueText.setText(String.valueOf(book.getTotalNum()));
        currentValueText.setText(String.valueOf(book.getCurrentNum()));

        boolean available = book.getCurrentNum() > 0;
        statusValueText.setText(available ? "可借" : "已借完");
        statusValueText.getStyleClass().removeAll("status-borrowed", "status-unavailable");
        statusValueText.getStyleClass().add(available ? "status-borrowed" : "status-unavailable");

        // 在线浏览按钮：有在线资源可点击，否则置灰并提示暂无在线资源
        boolean hasOnline = book.getResourceFile() != null && !book.getResourceFile().trim().isEmpty();
        if (hasOnline) {
            onlineReadButton.setText("在线浏览");
            onlineReadButton.setDisable(false);
        } else {
            onlineReadButton.setText("暂无在线资源");
            onlineReadButton.setDisable(true);
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
}
