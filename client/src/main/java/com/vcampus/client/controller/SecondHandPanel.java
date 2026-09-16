package com.vcampus.client.controller;

import com.vcampus.client.net.ClientSession;
import com.vcampus.client.net.SocketClient;
import com.vcampus.client.util.ScrollSpeedUtil;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.ChatMessageVO;
import com.vcampus.common.vo.ResourceFileVO;
import com.vcampus.common.vo.SecondHandVO;
import com.vcampus.common.vo.UserRole;
import com.vcampus.common.vo.UserVO;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 校园二手市场面板（学生上架 / 购买闲置物品）。
 *
 * <p>卡片式展示与校园超市一致；二手商品单件在售、无库存，被买走即下架。
 * 交易时买家扣款、卖家收款在同一事务内完成。</p>
 *
 * @author vCampus Team
 */
public class SecondHandPanel extends VBox {

    private static final ExecutorService THREAD_POOL = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "SecondHand-Thread-" + threadNumber.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    /** 全局共享连接：服务端把身份绑定在连接上，全客户端必须复用同一条 */
    private final SocketClient socketClient = ClientSession.client();

    private UserVO currentUser;
    private MainController mainController;

    private Label balanceValueLabel;
    /** 作为校园超市二级页时的返回回调（null 表示独立使用，不显示返回） */
    private Runnable onBack;
    private Button backBtn;
    private FlowPane cardFlowPane;
    /** 管理员审核入口按钮（仅 ADMIN 可见） */
    private Button reviewBtn;

    /** 列表页专用工具栏（进入聊天等二级页时隐藏） */
    private VBox headerTitleBox;
    private VBox headerBalanceBox;
    private Button myListBtn;
    private Button publishBtn;
    private Button refreshBtn;

    /** 多页面宿主：listings/review/myList/publish/confirmBuy/confirmOffShelf */
    private StackPane pageHost;
    private VBox listingsPage;
    private VBox reviewPage;
    private VBox myListPage;
    private VBox publishPage;
    private VBox confirmBuyPage;
    private VBox confirmOffShelfPage;
    private VBox editPricePage;
    private TextField editPriceField;
    private Label editPriceErrorLabel;
    /** 当前显示的页面（用于"← 返回"智能处理） */
    private VBox currentPage;
    /** 顶部消息条（替代 Alert，用于提示成功/失败/网络错误） */
    private HBox toastBar;
    private Label toastLabel;
    private PauseTransition toastTimer;

    /** 临时数据：发布表单、确认购买/下架的目标商品 */
    private TextField publishTitleField;
    private TextField publishPriceField;
    private TextArea publishDescField;
    private SecondHandVO pendingItem;

    /** 二手商品图片卡片尺寸（与校园超市保持一致） */
    private static final double IMAGE_W = 172.0;
    private static final double IMAGE_H = 120.0;
    /** 已解码图片缓存（服务端文件名 -> 图片），避免同一张图重复下载解码 */
    private final Map<String, Image> imageCache = new ConcurrentHashMap<>();
    /** “暂无图片”占位图，懒加载 */
    private Image placeholderImage;
    /** 发布页中本次待上传的新图片（null 表示未选择） */
    private ResourceFileVO publishPendingImage;
    private ImageView publishImagePreview;
    private Label publishImageStatus;
    /** 审核页/我的发布页 的列表容器（异步填充） */
    private VBox reviewListContainer;
    private VBox myListContainer;

    /** 聊天相关：聊天页 / 咨询列表页 / 聊天上下文 */
    private VBox chatPage;
    private VBox conversationsPage;
    private VBox chatMessagesContainer;
    private TextField chatInputField;
    private Label chatTitleLabel;
    private Label chatSubtitleLabel;
    /** 聊天页在顶部白框中的标题与刷新按钮 */
    private VBox chatHeaderBox;
    private Button chatRefreshBtn;
    /** 卖家会话列表轮询与增量比较状态 */
    private Timeline conversationsPolling;
    /** 二手市场可见期间的余额轮询，确保卖家及时看到收款结果 */
    private Timeline balancePolling;
    private String renderedConversationsSignature = null;
    private SecondHandVO chatItem;
    private String chatOtherUid;
    private String chatOtherName;
    /** 聊天消息滚动容器与轮询（1 秒，仅聊天页开启）相关状态 */
    private ScrollPane chatScroll;
    private Timeline chatPolling;
    private int renderedChatCount = 0;
    private Integer renderedLastChatId = null;
    private VBox conversationsContainer;

    public SecondHandPanel() {
        buildUi();
    }

    /**
     * 注入用户上下文并加载数据。
     */
    public void initData(UserVO user, MainController mainController) {
        this.currentUser = user;
        this.mainController = mainController;
        // 管理员显示审核入口
        if (reviewBtn != null) {
            boolean admin = isAdmin();
            reviewBtn.setVisible(admin);
            reviewBtn.setManaged(admin);
        }
        refresh();
    }

    private void buildUi() {
        String[] css = {"/css/tokens.css", "/css/base.css", "/css/library.css", "/css/shop.css"};
        for (String path : css) {
            java.net.URL url = getClass().getResource(path);
            if (url != null) {
                getStylesheets().add(url.toExternalForm());
            }
        }

        setSpacing(16.0);
        setPadding(new Insets(4.0));
        getStyleClass().add("shop-container");
        VBox.setVgrow(this, Priority.ALWAYS);
        // 面板隐藏时停止聊天轮询，避免后台请求泄漏
        visibleProperty().addListener((obs, was, is) -> {
            if (is) {
                startBalancePolling();
                if (currentPage == chatPage) {
                    startChatPolling();
                } else if (currentPage == conversationsPage) {
                    startConversationsPolling();
                }
            } else {
                stopBalancePolling();
                stopChatPolling();
                stopConversationsPolling();
            }
        });

        // 顶部消息条（替代 Alert 弹窗）
        toastLabel = new Label();
        toastLabel.setWrapText(true);
        toastLabel.getStyleClass().add("secondhand-toast-text");
        Region toastSpacer = new Region();
        HBox.setHgrow(toastSpacer, Priority.ALWAYS);
        Button toastCloseBtn = new Button("×");
        toastCloseBtn.getStyleClass().add("secondhand-toast-close");
        toastCloseBtn.setOnAction(e -> hideToast());
        toastBar = new HBox(10.0, toastLabel, toastSpacer, toastCloseBtn);
        toastBar.setAlignment(Pos.CENTER_LEFT);
        toastBar.getStyleClass().add("secondhand-toast");
        toastBar.setVisible(false);
        toastBar.setManaged(false);

        // 各页面 VBox
        listingsPage = wrapListingPage();
        reviewPage = buildReviewPage();
        myListPage = buildMyListPage();
        publishPage = buildPublishPage();
        confirmBuyPage = new VBox();
        confirmOffShelfPage = new VBox();
        editPricePage = new VBox();
        chatPage = buildChatPage();
        conversationsPage = buildConversationsPage();

        // 页面宿主：StackPane 互斥显示
        pageHost = new StackPane();
        VBox.setVgrow(pageHost, Priority.ALWAYS);
        pageHost.getChildren().addAll(listingsPage, reviewPage, myListPage,
                publishPage, confirmBuyPage, confirmOffShelfPage, editPricePage, chatPage, conversationsPage);
        showPage(listingsPage);

        getChildren().addAll(buildHeader(), toastBar, pageHost);
    }

    /**
     * 把商品浏览卡片包装为 listingsPage（占据 pageHost 的顶层）。
     */
    private VBox wrapListingPage() {
        VBox wrap = new VBox();
        wrap.getChildren().add(buildGridCard());
        VBox.setVgrow(wrap, Priority.ALWAYS);
        return wrap;
    }

    /**
     * 切换到指定页面（同一时刻仅一个页面可见且参与布局）。
     */
    private void showPage(VBox page) {
        if (page == null || pageHost == null) {
            return;
        }
        for (Node child : pageHost.getChildren()) {
            boolean visible = (child == page);
            child.setVisible(visible);
            child.setManaged(visible);
        }
        if (page != chatPage) {
            stopChatPolling();
        }
        if (page != conversationsPage) {
            stopConversationsPolling();
        }
        currentPage = page;
        updateBackButton();
    }

    /**
     * 智能返回：当前若在非顶层页面 → 回顶层 listings；
     * 当前在顶层 listings → 若有外部 onBack 则执行（嵌入式场景），否则隐藏。
     */
    private void handleBack() {
        if (currentPage != null && currentPage != listingsPage) {
            showPage(listingsPage);
            return;
        }
        if (onBack != null) {
            onBack.run();
        }
    }

    /**
     * 同步「← 返回」按钮的可见性。
     */
    private void updateBackButton() {
        if (backBtn == null) {
            return;
        }
        boolean onListings = (currentPage == listingsPage);
        boolean showBack = !onListings || onBack != null;
        backBtn.setVisible(showBack);
        backBtn.setManaged(showBack);
        setMarketToolbarVisible(onListings);
        setChatHeaderVisible(currentPage == chatPage);
    }

    /**
     * 聊天页：在顶部白框中显示对方名称/商品副标题与刷新按钮。
     */
    private void setChatHeaderVisible(boolean visible) {
        toggleNode(chatHeaderBox, visible);
        toggleNode(chatRefreshBtn, visible);
    }

    /**
     * 仅列表页显示市场工具栏；聊天/发布/审核等二级页只保留返回按钮。
     */
    private void setMarketToolbarVisible(boolean visible) {
        toggleNode(headerTitleBox, visible);
        toggleNode(headerBalanceBox, visible);
        toggleNode(myListBtn, visible);
        toggleNode(publishBtn, visible);
        toggleNode(refreshBtn, visible);
        if (reviewBtn != null) {
            boolean showReview = visible && isAdmin();
            reviewBtn.setVisible(showReview);
            reviewBtn.setManaged(showReview);
        }
    }

    private void toggleNode(Node node, boolean visible) {
        if (node != null) {
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    /**
     * 顶部卡片：标题 + 余额 + 发布/刷新。
     */
    private Node buildHeader() {
        VBox card = new VBox(14.0);
        card.getStyleClass().add("profile-card");

        HBox headerRow = new HBox(12.0);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        backBtn = new Button("← 返回");
        backBtn.getStyleClass().add("btn-recharge-preset");
        backBtn.setVisible(false);
        backBtn.setManaged(false);
        backBtn.setOnAction(e -> handleBack());

        // 聊天页专用：与「← 返回」同行的白框内标题（对方名称）与刷新按钮
        chatTitleLabel = new Label();
        chatTitleLabel.getStyleClass().add("chat-title-large");
        chatTitleLabel.setWrapText(true);
        chatSubtitleLabel = new Label();
        chatSubtitleLabel.getStyleClass().add("chat-title-sub");
        chatSubtitleLabel.setWrapText(true);
        chatHeaderBox = new VBox(4.0, chatTitleLabel, chatSubtitleLabel);
        HBox.setHgrow(chatHeaderBox, Priority.ALWAYS);
        chatHeaderBox.setVisible(false);
        chatHeaderBox.setManaged(false);

        chatRefreshBtn = new Button("刷新");
        chatRefreshBtn.getStyleClass().add("btn-recharge-preset");
        chatRefreshBtn.setOnAction(e -> loadChatHistory());
        chatRefreshBtn.setVisible(false);
        chatRefreshBtn.setManaged(false);

        headerTitleBox = new VBox(4.0);
        Label title = new Label("二手市场");
        title.getStyleClass().add("lib-title");
        Label subtitle = new Label("学生闲置好物，上架你的旧物或淘到心仪宝贝");
        subtitle.getStyleClass().add("lib-subtitle");
        headerTitleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        headerBalanceBox = new VBox(2.0);
        headerBalanceBox.setAlignment(Pos.CENTER_RIGHT);
        Label balanceCaption = new Label("校园卡余额");
        balanceCaption.getStyleClass().add("shop-balance-label");
        balanceValueLabel = new Label("¥ 0.00");
        balanceValueLabel.getStyleClass().add("shop-balance-value");
        headerBalanceBox.getChildren().addAll(balanceCaption, balanceValueLabel);

        myListBtn = new Button("我的发布");
        myListBtn.getStyleClass().add("btn-recharge-preset");
        myListBtn.setOnAction(e -> openMyListPage());

        reviewBtn = new Button("审核");
        reviewBtn.getStyleClass().add("btn-recharge-preset");
        reviewBtn.setVisible(false);
        reviewBtn.setManaged(false);
        reviewBtn.setOnAction(e -> openReviewPage());

        publishBtn = new Button("发布闲置");
        publishBtn.getStyleClass().add("btn-primary-action");
        publishBtn.setGraphic(SvgIconsPlaceholder.plus());
        publishBtn.setOnAction(e -> openPublishPage());

        refreshBtn = new Button("刷新");
        refreshBtn.getStyleClass().add("btn-recharge-preset");
        refreshBtn.setOnAction(e -> refresh());

        headerRow.getChildren().addAll(backBtn, headerTitleBox, chatHeaderBox, spacer, headerBalanceBox, myListBtn, reviewBtn, publishBtn, refreshBtn, chatRefreshBtn);
        card.getChildren().add(headerRow);
        return card;
    }

    /**
     * 中部卡片：在售商品卡片网格。
     */
    private Node buildGridCard() {
        VBox card = new VBox(12.0);
        card.getStyleClass().add("profile-card");
        VBox.setVgrow(card, Priority.ALWAYS);

        HBox header = new HBox(12.0);
        header.setAlignment(Pos.CENTER_LEFT);
        Label sectionTitle = new Label("在售商品");
        sectionTitle.getStyleClass().add("lib-section-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label hint = new Label("单件出售，被买走即下架");
        hint.getStyleClass().add("lib-subtitle");
        header.getChildren().addAll(sectionTitle, spacer, hint);

        cardFlowPane = new FlowPane();
        cardFlowPane.setHgap(14.0);
        cardFlowPane.setVgap(14.0);
        cardFlowPane.getStyleClass().add("shop-card-flow");

        ScrollPane scroll = new ScrollPane(cardFlowPane);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(false);
        scroll.getStyleClass().add("shop-card-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        ScrollSpeedUtil.applyCustomScrollSpeed(scroll);

        card.getChildren().addAll(header, scroll);
        return card;
    }

    /**
     * 构建「审核二手商品」页面（含标题与待审核列表容器）。
     */
    private VBox buildReviewPage() {
        VBox page = new VBox(12.0);
        page.getStyleClass().add("secondhand-subpage");
        HBox header = new HBox(12.0);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("审核二手商品");
        title.getStyleClass().add("lib-section-title");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Label hint = new Label("通过后即可上架");
        hint.getStyleClass().add("lib-subtitle");
        header.getChildren().addAll(title, sp, hint);

        reviewListContainer = new VBox(8.0);
        reviewListContainer.setPadding(new Insets(8.0, 0, 8.0, 0));

        ScrollPane scroll = new ScrollPane(reviewListContainer);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("shop-card-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        ScrollSpeedUtil.applyCustomScrollSpeed(scroll);

        page.getChildren().addAll(header, scroll);
        return page;
    }

    /**
     * 构建「我的发布」页面。
     */
    private VBox buildMyListPage() {
        VBox page = new VBox(12.0);
        page.getStyleClass().add("secondhand-subpage");
        HBox header = new HBox(12.0);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("我的发布");
        title.getStyleClass().add("lib-section-title");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Label hint = new Label("查看上架商品的审核/交易状态");
        hint.getStyleClass().add("lib-subtitle");
        header.getChildren().addAll(title, sp, hint);

        myListContainer = new VBox(8.0);
        myListContainer.setPadding(new Insets(8.0, 0, 8.0, 0));

        ScrollPane scroll = new ScrollPane(myListContainer);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("shop-card-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        ScrollSpeedUtil.applyCustomScrollSpeed(scroll);

        page.getChildren().addAll(header, scroll);
        return page;
    }

    /**
     * 构建「发布闲置」表单页面。
     */
    private VBox buildPublishPage() {
        VBox page = new VBox(14.0);
        page.getStyleClass().add("secondhand-subpage");
        HBox header = new HBox(12.0);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("发布闲置");
        title.getStyleClass().add("lib-section-title");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Label hint = new Label("填写后点击底部“提交审核”，等待管理员通过即可上架");
        hint.getStyleClass().add("lib-subtitle");
        header.getChildren().addAll(title, sp, hint);

        publishTitleField = new TextField();
        publishTitleField.setPromptText("商品标题，如：计算机组成原理（任国林版）二手书");
        publishTitleField.getStyleClass().add("modern-input-field");

        publishPriceField = new TextField();
        publishPriceField.setPromptText("定价，如 20.00");
        publishPriceField.getStyleClass().add("modern-input-field");

        publishDescField = new TextArea();
        publishDescField.setPromptText("描述（新旧程度、备注等，可选）");
        publishDescField.setPrefRowCount(4);
        publishDescField.getStyleClass().add("modern-input-field");

        // 商品图片：可选，未选择时展示“暂无图片”占位图
        publishImagePreview = new ImageView(getPlaceholderImage());
        publishImagePreview.setFitWidth(120.0);
        publishImagePreview.setFitHeight(80.0);
        publishImagePreview.setPreserveRatio(true);
        publishImagePreview.setSmooth(true);
        publishImagePreview.getStyleClass().add("shop-dialog-image-preview");

        publishImageStatus = new Label("未选择（商品将显示“暂无图片”）");
        publishImageStatus.getStyleClass().add("lib-subtitle");

        Button publishChooseImageBtn = new Button("选择图片…");
        publishChooseImageBtn.getStyleClass().add("btn-recharge-preset");
        Button publishClearImageBtn = new Button("清除");
        publishClearImageBtn.getStyleClass().add("lib-btn-danger");
        publishClearImageBtn.setDisable(true);

        Runnable syncPublishImage = () -> {
            if (publishPendingImage != null && publishPendingImage.getData() != null) {
                publishImagePreview.setImage(new Image(new ByteArrayInputStream(publishPendingImage.getData())));
                publishImageStatus.setText("已选择：" + publishPendingImage.getFileName());
                publishClearImageBtn.setDisable(false);
            } else {
                publishImagePreview.setImage(getPlaceholderImage());
                publishImageStatus.setText("未选择（商品将显示“暂无图片”）");
                publishClearImageBtn.setDisable(true);
            }
        };
        publishChooseImageBtn.setOnAction(e -> {
            ResourceFileVO picked = pickImageFile();
            if (picked != null) {
                publishPendingImage = picked;
                syncPublishImage.run();
            }
        });
        publishClearImageBtn.setOnAction(e -> {
            publishPendingImage = null;
            syncPublishImage.run();
        });

        HBox publishImageControls = new HBox(8.0, publishChooseImageBtn, publishClearImageBtn);
        publishImageControls.setAlignment(Pos.CENTER_LEFT);
        VBox publishImageCell = new VBox(6.0, publishImagePreview, publishImageControls, publishImageStatus);
        publishImageCell.setAlignment(Pos.CENTER_LEFT);

        GridPane grid = new GridPane();
        grid.setHgap(10.0);
        grid.setVgap(10.0);
        grid.setPadding(new Insets(16.0));
        grid.add(new Label("标题"), 0, 0);
        grid.add(publishTitleField, 1, 0);
        grid.add(new Label("定价"), 0, 1);
        grid.add(publishPriceField, 1, 1);
        grid.add(new Label("描述"), 0, 2);
        grid.add(publishDescField, 1, 2);
        grid.add(new Label("图片"), 0, 3);
        grid.add(publishImageCell, 1, 3);
        GridPane.setHgrow(publishTitleField, Priority.ALWAYS);
        GridPane.setHgrow(publishPriceField, Priority.ALWAYS);
        GridPane.setHgrow(publishDescField, Priority.ALWAYS);

        HBox footer = new HBox(10.0);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(0, 16.0, 16.0, 16.0));
        Region footSpacer = new Region();
        HBox.setHgrow(footSpacer, Priority.ALWAYS);
        Button cancelBtn = new Button("取消");
        cancelBtn.getStyleClass().add("btn-recharge-preset");
        cancelBtn.setOnAction(e -> {
            clearPublishForm();
            showPage(listingsPage);
        });
        Button submitBtn = new Button("提交审核");
        submitBtn.getStyleClass().add("btn-primary-action");
        submitBtn.setOnAction(e -> submitPublish());
        footer.getChildren().addAll(footSpacer, cancelBtn, submitBtn);

        page.getChildren().addAll(header, grid, footer);
        return page;
    }

    /**
     * 提交发布表单（校验 + 调用服务端）。
     */
    private void submitPublish() {
        String title = publishTitleField.getText() == null ? "" : publishTitleField.getText().trim();
        String priceRaw = publishPriceField.getText() == null ? "" : publishPriceField.getText().trim();
        if (title.isEmpty()) {
            showToast("请填写标题", ToastType.WARNING);
            return;
        }
        BigDecimal price;
        try {
            price = new BigDecimal(priceRaw);
        } catch (NumberFormatException e) {
            showToast("定价格式不正确，请输入数字（如 20.00）", ToastType.WARNING);
            return;
        }
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            showToast("定价必须大于 0", ToastType.WARNING);
            return;
        }
        SecondHandVO vo = new SecondHandVO();
        vo.setTitle(title);
        vo.setPrice(price);
        vo.setDescription(publishDescField.getText() == null ? "" : publishDescField.getText().trim());
        vo.setImage("");
        publishWithImage(vo);
    }

    /**
     * 发布前若有选择的图片则先上传，拿到服务端文件名后再提交商品。
     */
    private void publishWithImage(SecondHandVO vo) {
        final ResourceFileVO pending = publishPendingImage;
        if (pending == null || pending.getData() == null) {
            publish(vo);
            return;
        }
        showToast("正在上传图片…", ToastType.INFO);
        THREAD_POOL.execute(() -> {
            try {
                Message upload = new Message(currentUser.getAccountNumber(),
                        MessageType.SECOND_HAND_IMAGE_UPLOAD, null, pending);
                Message response = socketClient.send(upload);
                if (response != null && response.getCode() == ResponseCode.SUCCESS
                        && response.getData() instanceof String) {
                    String name = String.valueOf(response.getData());
                    if (!name.isEmpty() && !"null".equals(name)) {
                        vo.setImage(name);
                        Platform.runLater(() -> publish(vo));
                        return;
                    }
                }
                Platform.runLater(() -> showToast("图片上传失败，请稍后重试", ToastType.ERROR));
            } catch (Exception e) {
                Platform.runLater(() -> showToast("网络错误：" + e.getMessage(), ToastType.ERROR));
            }
        });
    }

    private void clearPublishForm() {
        if (publishTitleField != null) {
            publishTitleField.clear();
        }
        if (publishPriceField != null) {
            publishPriceField.clear();
        }
        if (publishDescField != null) {
            publishDescField.clear();
        }
        publishPendingImage = null;
        if (publishImagePreview != null) {
            publishImagePreview.setImage(getPlaceholderImage());
        }
        if (publishImageStatus != null) {
            publishImageStatus.setText("未选择（商品将显示“暂无图片”）");
        }
    }

    /**
     * 构建聊天页：标题（商品 + 对方）、消息列表、底部输入区。
     */
    private VBox buildChatPage() {
        VBox page = new VBox(12.0);
        page.getStyleClass().add("secondhand-subpage");


        chatMessagesContainer = new VBox(8.0);
        chatMessagesContainer.setPadding(new Insets(8.0, 4.0, 8.0, 4.0));

        chatScroll = new ScrollPane(chatMessagesContainer);
        chatScroll.setFitToWidth(true);
        chatScroll.getStyleClass().add("shop-card-scroll");
        VBox.setVgrow(chatScroll, Priority.ALWAYS);
        ScrollSpeedUtil.applyCustomScrollSpeed(chatScroll);

        chatInputField = new TextField();
        chatInputField.setPromptText("输入消息…");
        chatInputField.getStyleClass().addAll("modern-input-field", "chat-input-field");
        chatInputField.setOnAction(e -> sendChatMessage());
        Button sendBtn = new Button("发送");
        sendBtn.getStyleClass().add("btn-primary-action");
        sendBtn.setOnAction(e -> sendChatMessage());
        HBox inputRow = new HBox(8.0, chatInputField, sendBtn);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(chatInputField, Priority.ALWAYS);

        page.getChildren().addAll(chatScroll, inputRow);
        return page;
    }

    /**
     * 构建咨询列表页（卖家查看某商品的咨询买家）。
     */
    private VBox buildConversationsPage() {
        VBox page = new VBox(12.0);
        page.getStyleClass().add("secondhand-subpage");
        Label title = new Label("联系买家");
        title.getStyleClass().add("lib-section-title");
        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        Button refreshConvBtn = new Button("刷新");
        refreshConvBtn.getStyleClass().add("btn-recharge-preset");
        refreshConvBtn.setOnAction(e -> loadConversations());
        HBox titleRow = new HBox(10.0, title, titleSpacer, refreshConvBtn);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        conversationsContainer = new VBox(8.0);
        conversationsContainer.setPadding(new Insets(8.0, 0, 8.0, 0));
        ScrollPane scroll = new ScrollPane(conversationsContainer);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("shop-card-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        ScrollSpeedUtil.applyCustomScrollSpeed(scroll);
        page.getChildren().addAll(titleRow, scroll);
        return page;
    }

    /**
     * 打开与指定用户的聊天页。
     */
    private void openChatWith(SecondHandVO item, String otherUid, String otherName) {
        this.chatItem = item;
        this.chatOtherUid = otherUid;
        this.chatOtherName = (otherName == null || otherName.isEmpty()) ? otherUid : otherName;
        if (chatTitleLabel != null) {
            chatTitleLabel.setText(this.chatOtherName);
        }
        if (chatSubtitleLabel != null) {
            chatSubtitleLabel.setText("关于「" + item.getTitle() + "」");
        }
        if (chatMessagesContainer != null) {
            chatMessagesContainer.getChildren().clear();
            Label loading = new Label("暂无消息，开启一段对话吧～");
            loading.getStyleClass().add("lib-subtitle");
            chatMessagesContainer.getChildren().add(loading);
        }
        if (chatInputField != null) {
            chatInputField.clear();
        }
        renderedChatCount = 0;
        renderedLastChatId = null;
        showPage(chatPage);
        loadChatHistory();
        startChatPolling();
    }

    /**
     * 打开某商品的咨询列表页（卖家视角）。
     */
    private void openConversationsPage(SecondHandVO item) {
        this.chatItem = item;
        if (conversationsContainer != null) {
            conversationsContainer.getChildren().clear();
            Label loading = new Label("加载中…");
            loading.getStyleClass().add("lib-subtitle");
            conversationsContainer.getChildren().add(loading);
        }
        renderedConversationsSignature = null;
        showPage(conversationsPage);
        loadConversations();
        startConversationsPolling();
    }

    /**
     * 拉取当前会话历史消息：仅在有新消息时增量追加，避免整表重绘导致闪烁。
     */
    private void loadChatHistory() {
        if (chatItem == null || chatOtherUid == null) {
            return;
        }
        ChatMessageVO req = new ChatMessageVO();
        req.setItemId(chatItem.getId());
        req.setToUid(chatOtherUid);
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.CHAT_HISTORY, null, req);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (chatMessagesContainer == null) {
                        return;
                    }
                    if (response == null || response.getCode() != ResponseCode.SUCCESS
                            || !(response.getData() instanceof List)) {
                        if (renderedChatCount == 0) {
                            chatMessagesContainer.getChildren().clear();
                            chatMessagesContainer.getChildren().add(new Label(errorText(response, "加载失败")));
                        }
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    List<ChatMessageVO> msgs = (List<ChatMessageVO>) response.getData();
                    renderChatMessages(msgs);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (renderedChatCount == 0 && chatMessagesContainer != null) {
                        chatMessagesContainer.getChildren().clear();
                        chatMessagesContainer.getChildren().add(new Label("网络错误: " + e.getMessage()));
                    }
                });
            }
        });
    }

    /**
     * 增量渲染：数量/末条一致则跳过；新增则只追加；末条不一致则整体重绘。
     */
    private void renderChatMessages(List<ChatMessageVO> msgs) {
        int serverCount = msgs == null ? 0 : msgs.size();
        Integer serverLastId = serverCount == 0 ? null : msgs.get(serverCount - 1).getId();

        if (serverCount == renderedChatCount && Objects.equals(serverLastId, renderedLastChatId)) {
            return;
        }

        boolean canAppend = serverCount > renderedChatCount
                && renderedChatCount > 0
                && Objects.equals(msgs.get(renderedChatCount - 1).getId(), renderedLastChatId);

        if (canAppend) {
            for (int i = renderedChatCount; i < serverCount; i++) {
                chatMessagesContainer.getChildren().add(renderChatBubble(msgs.get(i)));
            }
        } else {
            chatMessagesContainer.getChildren().clear();
            if (serverCount == 0) {
                Label empty = new Label("暂无消息，开启一段对话吧～");
                empty.getStyleClass().add("lib-subtitle");
                chatMessagesContainer.getChildren().add(empty);
            } else {
                for (ChatMessageVO m : msgs) {
                    chatMessagesContainer.getChildren().add(renderChatBubble(m));
                }
            }
        }

        renderedChatCount = serverCount;
        renderedLastChatId = serverLastId;
        scrollChatToBottom();
    }

    /**
     * 滚动到聊天底部（显示最新消息）。
     */
    private void scrollChatToBottom() {
        if (chatScroll == null) {
            return;
        }
        chatScroll.applyCss();
        chatScroll.layout();
        chatScroll.setVvalue(1.0);
        Platform.runLater(() -> chatScroll.setVvalue(1.0));
    }

    /**
     * 启动聊天轮询（每 1 秒拉取一次历史消息，仅在聊天页且面板可见时执行）。
     */
    private void startChatPolling() {
        // 仅在聊天页且面板可见时才启动轮询
        if (!isVisible() || currentPage != chatPage) {
            return;
        }
        stopChatPolling();
        chatPolling = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (isVisible() && currentPage == chatPage) {
                loadChatHistory();
            } else {
                stopChatPolling();
            }
        }));
        chatPolling.setCycleCount(Timeline.INDEFINITE);
        chatPolling.play();
    }

    /**
     * 停止聊天轮询，避免关闭聊天页后继续后台请求。
     */
    private void stopChatPolling() {
        if (chatPolling != null) {
            chatPolling.stop();
            chatPolling = null;
        }
    }

    /**
     * 启动卖家会话列表轮询（每 1 秒，仅在会话列表页且面板可见时执行）。
     */
    private void startConversationsPolling() {
        if (!isVisible() || currentPage != conversationsPage) {
            return;
        }
        stopConversationsPolling();
        conversationsPolling = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (isVisible() && currentPage == conversationsPage) {
                loadConversations();
            } else {
                stopConversationsPolling();
            }
        }));
        conversationsPolling.setCycleCount(Timeline.INDEFINITE);
        conversationsPolling.play();
    }

    /**
     * 停止卖家会话列表轮询。
     */
    private void stopConversationsPolling() {
        if (conversationsPolling != null) {
            conversationsPolling.stop();
            conversationsPolling = null;
        }
    }

    /**
     * 二手市场可见时每秒刷新一次余额，使其他实例完成交易后卖家页面能及时更新。
     */
    private void startBalancePolling() {
        stopBalancePolling();
        balancePolling = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (isVisible() && getScene() != null) {
                refreshBalance();
            } else {
                stopBalancePolling();
            }
        }));
        balancePolling.setCycleCount(Timeline.INDEFINITE);
        balancePolling.play();
    }

    private void stopBalancePolling() {
        if (balancePolling != null) {
            balancePolling.stop();
            balancePolling = null;
        }
    }
    /**
     * 发送当前输入框里的消息。
     */
    private void sendChatMessage() {
        if (chatItem == null || chatOtherUid == null || chatInputField == null) {
            return;
        }
        String text = chatInputField.getText() == null ? "" : chatInputField.getText().trim();
        if (text.isEmpty()) {
            showToast("消息不能为空", ToastType.WARNING);
            return;
        }
        ChatMessageVO req = new ChatMessageVO();
        req.setItemId(chatItem.getId());
        req.setToUid(chatOtherUid);
        req.setContent(text);
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.CHAT_SEND, null, req);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS) {
                        if (chatInputField != null) {
                            chatInputField.clear();
                        }
                        loadChatHistory();
                    } else {
                        String msg = (response != null && response.getData() instanceof String)
                                ? (String) response.getData() : "请稍后重试";
                        showToast("发送失败：" + msg, ToastType.ERROR);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showToast("网络错误：" + e.getMessage(), ToastType.ERROR));
            }
        });
    }

    /**
     * 渲染单条聊天气泡（自己的靠右，对方的靠左）。
     */
    private Node renderChatBubble(ChatMessageVO msg) {
        boolean mine = currentUser != null && currentUser.getAccountNumber() != null
                && currentUser.getAccountNumber().equals(msg.getFromUid());

        VBox bubble = new VBox(3.0);
        bubble.setMaxWidth(420.0);

        Label name = new Label(mine ? "我" : (msg.getFromName() == null || msg.getFromName().isEmpty()
                ? msg.getFromUid() : msg.getFromName()));
        name.getStyleClass().add("chat-bubble-name");

        Label content = new Label(msg.getContent() == null ? "" : msg.getContent());
        content.setWrapText(true);
        content.getStyleClass().add(mine ? "chat-bubble-mine" : "chat-bubble-other");

        Label time = new Label(msg.getSendTime() == null ? "" : msg.getSendTime());
        time.getStyleClass().add("chat-bubble-time");

        bubble.getChildren().addAll(name, content, time);
        bubble.setAlignment(mine ? Pos.TOP_RIGHT : Pos.TOP_LEFT);
        if (mine) {
            name.setAlignment(Pos.TOP_RIGHT);
            time.setAlignment(Pos.TOP_RIGHT);
        }

        HBox row = new HBox(8.0);
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        if (mine) {
            row.getChildren().addAll(spacer, bubble);
        } else {
            row.getChildren().addAll(bubble, spacer);
        }
        return row;
    }

    /**
     * 拉取某商品的咨询会话列表（卖家视角）。
     */
    private void loadConversations() {
        if (chatItem == null) {
            return;
        }
        ChatMessageVO req = new ChatMessageVO();
        req.setItemId(chatItem.getId());
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.CHAT_CONVERSATIONS, null, req);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (conversationsContainer == null) {
                        return;
                    }
                    if (response == null || response.getCode() != ResponseCode.SUCCESS
                            || !(response.getData() instanceof List)) {
                        if (renderedConversationsSignature == null) {
                            conversationsContainer.getChildren().clear();
                            conversationsContainer.getChildren().add(new Label(errorText(response, "加载失败")));
                        }
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    List<ChatMessageVO> convs = (List<ChatMessageVO>) response.getData();
                    String signature = buildConversationsSignature(convs);
                    if (signature.equals(renderedConversationsSignature)) {
                        return; // 会话列表无变化，跳过重绘
                    }
                    renderedConversationsSignature = signature;

                    conversationsContainer.getChildren().clear();
                    if (convs == null || convs.isEmpty()) {
                        Label empty = new Label("暂无买家咨询");
                        empty.getStyleClass().add("lib-subtitle");
                        conversationsContainer.getChildren().add(empty);
                    } else {
                        for (ChatMessageVO c : convs) {
                            conversationsContainer.getChildren().add(renderConversationRow(c));
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (conversationsContainer != null && renderedConversationsSignature == null) {
                        conversationsContainer.getChildren().clear();
                        conversationsContainer.getChildren().add(new Label("网络错误: " + e.getMessage()));
                    }
                });
            }
        });
    }

    /**
     * 生成会话列表签名（每条会话的最新消息 id + 内容），用于判断是否需要重绘。
     */
    private String buildConversationsSignature(List<ChatMessageVO> convs) {
        StringBuilder sb = new StringBuilder();
        if (convs != null) {
            for (ChatMessageVO c : convs) {
                sb.append(c.getId()).append('|')
                  .append(c.getFromUid()).append('|')
                  .append(c.getContent()).append(';');
            }
        }
        return sb.toString();
    }
    /**
     * 渲染单条咨询会话（卖家视角：显示买家 + 最后一条消息 + 回复按钮）。
     */
    private Node renderConversationRow(ChatMessageVO c) {
        String otherUid = c.getFromUid() == null ? "" : c.getFromUid();
        String otherName = (c.getFromName() == null || c.getFromName().isEmpty()) ? otherUid : c.getFromName();

        HBox row = new HBox(10.0);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("profile-card");
        row.setPadding(new Insets(10.0));

        VBox infoBox = new VBox(3.0);
        Label name = new Label(otherName);
        name.getStyleClass().add("shop-card-name");
        Label last = new Label(c.getContent() == null ? "" : c.getContent());
        last.getStyleClass().add("lib-subtitle");
        last.setWrapText(true);
        Label time = new Label(c.getSendTime() == null ? "" : c.getSendTime());
        time.getStyleClass().add("lib-subtitle");
        infoBox.getChildren().addAll(name, last, time);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button openBtn = new Button("回复");
        openBtn.getStyleClass().add("btn-primary-action");
        final String fOtherUid = otherUid;
        openBtn.setOnAction(e -> openChatWith(chatItem, fOtherUid, otherName));

        row.getChildren().addAll(infoBox, spacer, openBtn);
        return row;
    }

    /**
     * 设置返回回调（被校园超市当作二级页嵌入时调用）。
     */
    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
        updateBackButton();
    }

    /**
     * 仅刷新在售列表与余额。
     */
    public void refresh() {
        refreshListings();
        refreshBalance();
    }

    private void refreshListings() {
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.SECOND_HAND_QUERY, null, null);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS
                            && response.getData() instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<SecondHandVO> items = (List<SecondHandVO>) response.getData();
                        rebuildCards(items);
                    } else {
                        showToast("读取失败：" + errorText(response, "无法读取二手市场"), ToastType.ERROR);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showToast("网络错误：" + e.getMessage(), ToastType.ERROR));
            }
        });
    }

    private void refreshBalance() {
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.PAYMENT_BALANCE, null, null);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS
                            && response.getData() instanceof UserVO) {
                        UserVO fresh = (UserVO) response.getData();
                        currentUser.setBalance(fresh.getBalance());
                        BigDecimal b = fresh.getBalance() == null ? BigDecimal.ZERO : fresh.getBalance();
                        balanceValueLabel.setText("¥ " + b.setScale(2, RoundingMode.HALF_UP).toPlainString());
                        if (mainController != null) {
                            mainController.updateBalance(b);
                        }
                    }
                });
            } catch (Exception ignored) {
                // 余额刷新失败不打断
            }
        });
    }

    private void rebuildCards(List<SecondHandVO> items) {
        cardFlowPane.getChildren().clear();
        if (items == null || items.isEmpty()) {
            Label empty = new Label("暂无在售二手商品，点击右上角“发布闲置”开始出闲置～");
            empty.getStyleClass().add("shop-cart-empty");
            empty.setPadding(new Insets(24.0, 0, 24.0, 0));
            cardFlowPane.getChildren().add(empty);
            return;
        }
        for (SecondHandVO item : items) {
            cardFlowPane.getChildren().add(createCard(item));
        }
    }

    private Node createCard(SecondHandVO item) {
        VBox card = new VBox(8.0);
        card.getStyleClass().add("shop-card");
        card.setAlignment(Pos.TOP_LEFT);
        card.setPadding(new Insets(14.0));
        card.setPrefWidth(220.0);
        card.setMinWidth(200.0);
        card.setMaxWidth(240.0);

        boolean mine = currentUser != null && currentUser.getAccountNumber() != null
                && currentUser.getAccountNumber().equals(item.getSellerId());

        // 商品图片（无图时显示“暂无图片”占位图）
        StackPane imageBox = new StackPane();
        imageBox.setPrefSize(IMAGE_W, IMAGE_H);
        imageBox.setMinSize(IMAGE_W, IMAGE_H);
        imageBox.setMaxSize(IMAGE_W, IMAGE_H);
        imageBox.getStyleClass().add("shop-card-image-box");
        imageBox.getChildren().add(createItemImageView(item));

        // 顶部：状态徽标 + 右上角"联系卖家/咨询"按钮
        HBox topRow = new HBox(6.0);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label badge = new Label("在售");
        badge.getStyleClass().addAll("shop-card-badge", "shop-card-badge-on");
        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        Button chatBtn = new Button(mine ? "联系买家" : "联系卖家");
        chatBtn.getStyleClass().add("secondhand-chat-btn");
        if (mine) {
            chatBtn.setOnAction(e -> openConversationsPage(item));
        } else {
            chatBtn.setOnAction(e -> openChatWith(item, item.getSellerId(), item.getSellerName()));
        }
        topRow.getChildren().addAll(badge, topSpacer, chatBtn);

        Label title = new Label(item.getTitle() == null ? "" : item.getTitle());
        title.getStyleClass().add("shop-card-name");
        title.setWrapText(true);

        Label seller = new Label("卖家 " + (item.getSellerName() == null ? "" : item.getSellerName()));
        seller.getStyleClass().add("lib-subtitle");

        Label desc = new Label(item.getDescription() == null ? "" : item.getDescription());
        desc.getStyleClass().add("shop-card-desc");
        desc.setWrapText(true);
        desc.setMaxHeight(44.0);
        desc.setMinHeight(34.0);
        VBox.setVgrow(desc, Priority.ALWAYS);

        Label price = new Label(formatPrice(item.getPrice()));
        price.getStyleClass().add("shop-card-price");

        card.getChildren().addAll(imageBox, topRow, title, seller, desc, price);

        boolean available = "ON_SALE".equals(item.getStatus());
        if (mine && available) {
            Button editPriceBtn = new Button("修改价格");
            editPriceBtn.getStyleClass().add("btn-recharge-preset");
            editPriceBtn.setOnAction(e -> openEditPricePage(item));

            Button changeImageBtn = new Button("更换图片");
            changeImageBtn.getStyleClass().add("btn-recharge-preset");
            changeImageBtn.setOnAction(e -> chooseAndUpdateImage(item));

            Button offShelfBtn = new Button("下架");
            offShelfBtn.getStyleClass().add("lib-btn-danger");
            offShelfBtn.setOnAction(e -> confirmOffShelf(item));

            // 三个按钮在 220px 宽卡片内自动换行，避免横向溢出
            FlowPane actions = new FlowPane(6.0, 6.0, editPriceBtn, changeImageBtn, offShelfBtn);
            actions.setPrefWrapLength(192.0);
            card.getChildren().add(actions);
        } else {
            Button actionBtn = new Button(mine ? "下架" : "购买");
            actionBtn.setMaxWidth(Double.MAX_VALUE);
            actionBtn.setMinHeight(28.0);
            if (mine) {
                actionBtn.getStyleClass().add("lib-btn-danger");
                actionBtn.setOnAction(e -> confirmOffShelf(item));
            } else {
                actionBtn.getStyleClass().add("shop-btn-buy");
                actionBtn.setOnAction(e -> confirmBuy(item));
            }
            card.getChildren().add(actionBtn);
        }
        return card;
    }

    // ===================== 二手商品图片 =====================

    /**
     * 卡片图片视图：有商品图则异步下载展示，否则显示“暂无图片”占位图。
     */
    private ImageView createItemImageView(SecondHandVO item) {
        return createItemThumbnail(item, IMAGE_W, IMAGE_H);
    }

    /**
     * 生成指定尺寸的商品图片视图（列表行 / 详情页复用）。
     */
    private ImageView createItemThumbnail(SecondHandVO item, double width, double height) {
        ImageView view = new ImageView(getPlaceholderImage());
        view.setFitWidth(width);
        view.setFitHeight(height);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.getStyleClass().add("shop-card-image");

        String name = item == null ? null : item.getImage();
        if (name != null && !name.isEmpty()) {
            Image cached = imageCache.get(name);
            if (cached != null) {
                view.setImage(cached);
            } else {
                requestItemImage(name, view);
            }
        }
        return view;
    }

    /**
     * 把图片视图包进固定尺寸的圆角容器，保证列表中各缩略图外观一致。
     */
    private StackPane wrapThumbnail(ImageView view, double width, double height) {
        StackPane box = new StackPane(view);
        // 预留 2px 内边距，避免图片贴边
        box.setPrefSize(width + 4.0, height + 4.0);
        box.setMinSize(width + 4.0, height + 4.0);
        box.setMaxSize(width + 4.0, height + 4.0);
        box.getStyleClass().add("secondhand-thumb-box");
        return box;
    }

    /**
     * 异步下载二手商品图片，成功后写入缓存并刷新目标 ImageView。
     */
    private void requestItemImage(String name, ImageView target) {
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(),
                        MessageType.SECOND_HAND_IMAGE_DOWNLOAD, null, name);
                Message response = socketClient.send(request);
                if (response == null || response.getCode() != ResponseCode.SUCCESS
                        || !(response.getData() instanceof ResourceFileVO)) {
                    return; // 保持占位图
                }
                byte[] data = ((ResourceFileVO) response.getData()).getData();
                if (data == null || data.length == 0) {
                    return;
                }
                Image img = new Image(new ByteArrayInputStream(data));
                if (img.isError() || img.getWidth() <= 0) {
                    return; // 解码失败保持占位图
                }
                imageCache.put(name, img);
                Platform.runLater(() -> {
                    if (target != null) {
                        target.setImage(img);
                    }
                });
            } catch (Exception ignored) {
                // 下载失败时静默保持占位图，不打扰用户
            }
        });
    }

    /**
     * 懒加载“暂无图片”占位图（与校园超市共用同一张图）。
     */
    private Image getPlaceholderImage() {
        if (placeholderImage == null) {
            try (InputStream is = getClass().getResourceAsStream("/images/goods-no-image.png")) {
                placeholderImage = is != null ? new Image(is) : new WritableImage(1, 1);
            } catch (Exception e) {
                placeholderImage = new WritableImage(1, 1);
            }
        }
        return placeholderImage;
    }

    /**
     * 读取本地文件并上传到服务端，成功返回服务端文件名；失败返回 null。
     *
     * <p>阻塞方法，必须在工作线程调用。</p>
     */
    private String uploadImageBlocking(File file) throws Exception {
        byte[] data = Files.readAllBytes(file.toPath());
        if (data.length == 0) {
            return null;
        }
        ResourceFileVO payload = new ResourceFileVO();
        payload.setFileName(file.getName());
        payload.setData(data);
        Message request = new Message(currentUser.getAccountNumber(),
                MessageType.SECOND_HAND_IMAGE_UPLOAD, null, payload);
        Message response = socketClient.send(request);
        if (response == null || response.getCode() != ResponseCode.SUCCESS
                || !(response.getData() instanceof String)) {
            return null;
        }
        String name = String.valueOf(response.getData());
        if (name.isEmpty() || "null".equals(name)) {
            return null;
        }
        // 顺手缓存，换图成功后无需重新下载即可显示
        Image img = new Image(new ByteArrayInputStream(data));
        if (!img.isError() && img.getWidth() > 0) {
            imageCache.put(name, img);
        }
        return name;
    }

    /**
     * 尽力而为地删除服务端图片（不阻塞、不打扰用户）。
     */
    private void deleteImageQuietly(String imageName) {
        if (imageName == null || imageName.trim().isEmpty()) {
            return;
        }
        String name = imageName.trim();
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(),
                        MessageType.SECOND_HAND_IMAGE_DELETE, null, name);
                socketClient.send(request);
                imageCache.remove(name);
            } catch (Exception ignored) {
                // 删除失败不影响主流程
            }
        });
    }

    /**
     * 弹出文件选择框，读取图片字节。取消选择或读取失败返回 null。
     */
    private ResourceFileVO pickImageFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择商品图片");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("图片文件", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"),
                new FileChooser.ExtensionFilter("所有文件", "*.*"));
        File file = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (file == null) {
            return null;
        }
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            if (data.length == 0) {
                showToast("图片内容为空", ToastType.WARNING);
                return null;
            }
            ResourceFileVO vo = new ResourceFileVO();
            vo.setFileName(file.getName());
            vo.setData(data);
            return vo;
        } catch (Exception e) {
            showToast("读取图片失败：" + e.getMessage(), ToastType.ERROR);
            return null;
        }
    }

    /**
     * 卖家为自己发布的商品更换图片：先上传新图，再回填到商品。
     *
     * <p>若回填失败会尽力删除刚上传的新图，避免留下孤儿文件。</p>
     */
    private void chooseAndUpdateImage(SecondHandVO item) {
        if (item == null || item.getId() == null) {
            return;
        }
        ResourceFileVO picked = pickImageFile();
        if (picked == null) {
            return;
        }
        showToast("正在上传图片…", ToastType.INFO);
        final String oldImage = item.getImage();
        THREAD_POOL.execute(() -> {
            try {
                ResourceFileVO payload = new ResourceFileVO();
                payload.setFileName(picked.getFileName());
                payload.setData(picked.getData());
                Message upload = new Message(currentUser.getAccountNumber(),
                        MessageType.SECOND_HAND_IMAGE_UPLOAD, null, payload);
                Message uploadResp = socketClient.send(upload);
                if (uploadResp == null || uploadResp.getCode() != ResponseCode.SUCCESS
                        || !(uploadResp.getData() instanceof String)) {
                    Platform.runLater(() -> showToast("图片上传失败，请稍后重试", ToastType.ERROR));
                    return;
                }
                String newName = String.valueOf(uploadResp.getData());
                if (newName.isEmpty() || "null".equals(newName)) {
                    Platform.runLater(() -> showToast("图片上传失败，请稍后重试", ToastType.ERROR));
                    return;
                }

                SecondHandVO payload2 = new SecondHandVO();
                payload2.setId(item.getId());
                payload2.setImage(newName);
                Message update = new Message(currentUser.getAccountNumber(),
                        MessageType.SECOND_HAND_UPDATE_IMAGE, null, payload2);
                Message updateResp = socketClient.send(update);
                boolean ok = updateResp != null && updateResp.getCode() == ResponseCode.SUCCESS;

                Platform.runLater(() -> {
                    if (ok) {
                        item.setImage(newName);
                        imageCache.put(newName, new Image(new ByteArrayInputStream(picked.getData())));
                        showToast("商品图片已更新", ToastType.SUCCESS);
                        // 替换成功后再清理旧图
                        if (oldImage != null && !oldImage.isEmpty() && !oldImage.equals(newName)) {
                            deleteImageQuietly(oldImage);
                        }
                        refreshAfterImageChange();
                    } else {
                        showToast("修改图片失败：" + errorText(updateResp, "修改图片失败"), ToastType.ERROR);
                        deleteImageQuietly(newName);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showToast("网络错误：" + e.getMessage(), ToastType.ERROR));
            }
        });
    }

    /**
     * 卖家清除自己商品的图片，恢复“暂无图片”。
     */
    private void clearItemImage(SecondHandVO item) {
        if (item == null || item.getId() == null) {
            return;
        }
        final String oldImage = item.getImage();
        showToast("正在移除图片…", ToastType.INFO);
        THREAD_POOL.execute(() -> {
            try {
                SecondHandVO payload = new SecondHandVO();
                payload.setId(item.getId());
                payload.setImage("");
                Message request = new Message(currentUser.getAccountNumber(),
                        MessageType.SECOND_HAND_UPDATE_IMAGE, null, payload);
                Message response = socketClient.send(request);
                boolean ok = response != null && response.getCode() == ResponseCode.SUCCESS;
                Platform.runLater(() -> {
                    if (ok) {
                        item.setImage("");
                        showToast("已移除商品图片", ToastType.SUCCESS);
                        if (oldImage != null && !oldImage.isEmpty()) {
                            deleteImageQuietly(oldImage);
                        }
                        refreshAfterImageChange();
                    } else {
                        showToast("移除图片失败：" + errorText(response, "移除图片失败"), ToastType.ERROR);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showToast("网络错误：" + e.getMessage(), ToastType.ERROR));
            }
        });
    }

    /**
     * 换图 / 移除图片成功后刷新视图：停留在「我的发布」页则重载该页，否则刷新在售列表。
     */
    private void refreshAfterImageChange() {
        if (currentPage == myListPage) {
            openMyListPage();
        } else {
            refresh();
        }
    }

    /**
     * 发布闲置弹窗。
     */
    private void openPublishPage() {
        clearPublishForm();
        showPage(publishPage);
    }

    private void publish(SecondHandVO vo) {
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.SECOND_HAND_PUBLISH, null, vo);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS) {
                        showToast("已提交审核，等待管理员通过后上架", ToastType.SUCCESS);
                        clearPublishForm();
                        showPage(listingsPage);
                        refresh();
                    } else {
                        showToast("发布失败：" + errorText(response, "发布失败"), ToastType.ERROR);
                        // 发布未成功时清理刚上传的图片，避免服务端留下孤儿文件
                        String uploaded = vo.getImage();
                        if (uploaded != null && !uploaded.isEmpty()) {
                            deleteImageQuietly(uploaded);
                            vo.setImage("");
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showToast("网络错误：" + e.getMessage(), ToastType.ERROR));
            }
        });
    }

    private void confirmBuy(SecondHandVO item) {
        openConfirmBuyPage(item);
    }

    private void openConfirmBuyPage(SecondHandVO item) {
        pendingItem = item;
        confirmBuyPage.getChildren().clear();
        confirmBuyPage.getStyleClass().add("secondhand-subpage");
        confirmBuyPage.setSpacing(14.0);
        confirmBuyPage.setPadding(new Insets(16.0));

        Label title = new Label("确认购买");
        title.getStyleClass().add("lib-section-title");
        title.setWrapText(true);

        VBox summary = new VBox(8.0);
        summary.getStyleClass().add("profile-card");
        summary.setPadding(new Insets(16.0));
        Label tName = new Label(item.getTitle() == null ? "" : item.getTitle());
        tName.getStyleClass().add("shop-card-name");
        tName.setWrapText(true);
        Label tSeller = new Label("卖家：" + (item.getSellerName() == null ? "" : item.getSellerName()));
        tSeller.getStyleClass().add("lib-subtitle");
        Label tPrice = new Label("应付金额：" + formatPrice(item.getPrice()));
        tPrice.getStyleClass().addAll("lib-subtitle", "secondhand-confirm-price");

        // 商品图片预览（无图时显示占位图）
        StackPane confirmImageBox = wrapThumbnail(createItemThumbnail(item, 150.0, 105.0), 150.0, 105.0);
        VBox confirmInfoBox = new VBox(8.0, tName, tSeller, tPrice);
        HBox.setHgrow(confirmInfoBox, Priority.ALWAYS);
        HBox confirmBody = new HBox(14.0, confirmImageBox, confirmInfoBox);
        confirmBody.setAlignment(Pos.CENTER_LEFT);
        summary.getChildren().add(confirmBody);

        Label tip = new Label("货款将直接转入卖家账户；买下后该商品立即下架。");
        tip.getStyleClass().add("lib-subtitle");
        tip.setWrapText(true);

        HBox footer = new HBox(10.0);
        footer.setAlignment(Pos.CENTER_RIGHT);
        Region footSpacer = new Region();
        HBox.setHgrow(footSpacer, Priority.ALWAYS);
        Button cancelBtn = new Button("取消");
        cancelBtn.getStyleClass().add("btn-recharge-preset");
        cancelBtn.setOnAction(e -> showPage(listingsPage));
        Button okBtn = new Button("确认购买");
        okBtn.getStyleClass().add("btn-primary-action");
        okBtn.setOnAction(e -> {
            SecondHandVO target = pendingItem;
            showPage(listingsPage);
            if (target != null) {
                doBuy(target);
            }
        });
        footer.getChildren().addAll(footSpacer, cancelBtn, okBtn);

        confirmBuyPage.getChildren().addAll(title, summary, tip, footer);
        showPage(confirmBuyPage);
    }

    private void doBuy(SecondHandVO item) {
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.SECOND_HAND_BUY, null, item.getId());
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS) {
                        showToast("已购入「" + item.getTitle() + "」，卖家将收到货款。", ToastType.SUCCESS);
                        refresh();
                    } else {
                        showToast("购买失败：" + errorText(response, "购买失败"), ToastType.ERROR);
                        refreshListings();
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showToast("网络错误：" + e.getMessage(), ToastType.ERROR));
            }
        });
    }

    /**
     * 进入「修改价格」二级页（仅卖家本人的在售商品）。
     */
    private void openEditPricePage(SecondHandVO item) {
        editPricePage.getChildren().clear();
        editPricePage.getStyleClass().add("secondhand-subpage");
        editPricePage.setSpacing(14.0);
        editPricePage.setPadding(new Insets(16.0));

        Label title = new Label("修改价格");
        title.getStyleClass().add("lib-section-title");

        VBox summary = new VBox(8.0);
        summary.getStyleClass().add("profile-card");
        summary.setPadding(new Insets(16.0));
        Label name = new Label(item.getTitle() == null ? "" : item.getTitle());
        name.getStyleClass().add("shop-card-name");
        name.setWrapText(true);
        Label curPrice = new Label("当前价格：" + formatPrice(item.getPrice()));
        curPrice.getStyleClass().add("lib-subtitle");
        summary.getChildren().addAll(name, curPrice);

        VBox form = new VBox(8.0);
        form.getStyleClass().add("profile-card");
        form.setPadding(new Insets(16.0));

        Label inputCaption = new Label("新价格（0.01 ~ 99999.99）");
        inputCaption.getStyleClass().add("shop-form-label");
        editPriceField = new TextField(item.getPrice() == null ? "" : item.getPrice().stripTrailingZeros().toPlainString());
        editPriceField.getStyleClass().add("modern-input-field");
        editPriceField.setPromptText("请输入新价格，如 88.00");

        Label hint = new Label("价格修改立即生效");
        hint.getStyleClass().add("lib-subtitle");

        editPriceErrorLabel = new Label();
        editPriceErrorLabel.getStyleClass().addAll("lib-msg-label", "error");
        editPriceErrorLabel.setVisible(false);
        editPriceErrorLabel.setManaged(false);

        form.getChildren().addAll(inputCaption, editPriceField, hint, editPriceErrorLabel);

        HBox footer = new HBox(10.0);
        footer.setAlignment(Pos.CENTER_RIGHT);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button cancelBtn = new Button("取消");
        cancelBtn.getStyleClass().add("btn-recharge-preset");
        cancelBtn.setOnAction(e -> showPage(listingsPage));
        Button okBtn = new Button("确认");
        okBtn.getStyleClass().add("btn-primary-action");
        okBtn.setOnAction(e -> submitPriceChange(item));
        footer.getChildren().addAll(sp, cancelBtn, okBtn);

        editPricePage.getChildren().addAll(title, summary, form, footer);
        showPage(editPricePage);
    }

    /**
     * 提交改价：本地校验 0.01~99999.99（最多两位小数），成功返回列表并提示，失败内联红字。
     */
    private void submitPriceChange(SecondHandVO item) {
        String text = editPriceField == null || editPriceField.getText() == null ? "" : editPriceField.getText().trim();
        BigDecimal price;
        try {
            price = new BigDecimal(text);
        } catch (NumberFormatException e) {
            showEditPriceError("请输入合法的价格数字");
            return;
        }
        BigDecimal min = new BigDecimal("0.01");
        BigDecimal max = new BigDecimal("99999.99");
        if (price.compareTo(min) < 0 || price.compareTo(max) > 0) {
            showEditPriceError("价格需在 0.01 ~ 99999.99 之间");
            return;
        }
        if (price.stripTrailingZeros().scale() > 2) {
            showEditPriceError("价格最多保留两位小数");
            return;
        }
        hideEditPriceError();

        SecondHandVO payload = new SecondHandVO();
        payload.setId(item.getId());
        payload.setPrice(price);
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.SECOND_HAND_UPDATE_PRICE, null, payload);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS) {
                        showToast("价格已更新为 " + formatPrice(price), ToastType.SUCCESS);
                        showPage(listingsPage);
                        refresh();
                    } else {
                        showEditPriceError(errorText(response, "改价失败，请稍后重试"));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showEditPriceError("网络错误，改价失败"));
            }
        });
    }

    private void showEditPriceError(String msg) {
        if (editPriceErrorLabel != null) {
            editPriceErrorLabel.setText(msg);
            editPriceErrorLabel.setVisible(true);
            editPriceErrorLabel.setManaged(true);
        }
    }

    private void hideEditPriceError() {
        if (editPriceErrorLabel != null) {
            editPriceErrorLabel.setVisible(false);
            editPriceErrorLabel.setManaged(false);
        }
    }
    private void confirmOffShelf(SecondHandVO item) {
        openConfirmOffShelfPage(item);
    }

    private void openConfirmOffShelfPage(SecondHandVO item) {
        pendingItem = item;
        confirmOffShelfPage.getChildren().clear();
        confirmOffShelfPage.getStyleClass().add("secondhand-subpage");
        confirmOffShelfPage.setSpacing(14.0);
        confirmOffShelfPage.setPadding(new Insets(16.0));

        Label title = new Label("确认下架");
        title.getStyleClass().add("lib-section-title");

        VBox summary = new VBox(8.0);
        summary.getStyleClass().add("profile-card");
        summary.setPadding(new Insets(16.0));
        Label tName = new Label(item.getTitle() == null ? "" : item.getTitle());
        tName.getStyleClass().add("shop-card-name");
        tName.setWrapText(true);
        Label tPrice = new Label("当前定价：" + formatPrice(item.getPrice()));
        tPrice.getStyleClass().add("lib-subtitle");
        summary.getChildren().addAll(tName, tPrice);

        Label tip = new Label("下架后该商品将不再被其他同学看到，已上架的购买记录不受影响。");
        tip.getStyleClass().add("lib-subtitle");
        tip.setWrapText(true);

        HBox footer = new HBox(10.0);
        footer.setAlignment(Pos.CENTER_RIGHT);
        Region footSpacer = new Region();
        HBox.setHgrow(footSpacer, Priority.ALWAYS);
        Button cancelBtn = new Button("取消");
        cancelBtn.getStyleClass().add("btn-recharge-preset");
        cancelBtn.setOnAction(e -> showPage(listingsPage));
        Button okBtn = new Button("确认下架");
        okBtn.getStyleClass().add("btn-primary-action");
        okBtn.setOnAction(e -> {
            SecondHandVO target = pendingItem;
            showPage(listingsPage);
            if (target != null) {
                doOffShelf(target);
            }
        });
        footer.getChildren().addAll(footSpacer, cancelBtn, okBtn);

        confirmOffShelfPage.getChildren().addAll(title, summary, tip, footer);
        showPage(confirmOffShelfPage);
    }

    private void doOffShelf(SecondHandVO item) {
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.SECOND_HAND_OFF_SHELF, null, item.getId());
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS) {
                        showToast("已下架「" + item.getTitle() + "」", ToastType.SUCCESS);
                        refresh();
                    } else {
                        showToast("下架失败：" + errorText(response, "下架失败"), ToastType.ERROR);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showToast("网络错误：" + e.getMessage(), ToastType.ERROR));
            }
        });
    }

    /**
     * 判断当前用户是否为管理员。
     */
    private boolean isAdmin() {
        return currentUser != null && currentUser.getRole() == UserRole.ADMIN;
    }

    /**
     * 状态码转中文标签。
     */
    private String statusLabel(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case "PENDING" -> "待审核";
            case "ON_SALE" -> "在售";
            case "SOLD" -> "已售/已下架";
            case "REJECTED" -> "审核未通过";
            default -> status;
        };
    }

    /**
     * 根据状态返回徽标样式类。
     */
    private String statusBadgeClass(String status) {
        return switch (status == null ? "" : status) {
            case "ON_SALE" -> "shop-card-badge-on";
            case "PENDING" -> "shop-card-badge-pending";
            case "REJECTED" -> "shop-card-badge-rejected";
            default -> "shop-card-badge-off";
        };
    }

    /**
     * 管理员审核入口：进入「审核二手商品」页面，加载待审核列表。
     */
    private void openReviewPage() {
        if (reviewListContainer == null) {
            return;
        }
        reviewListContainer.getChildren().clear();
        reviewListContainer.getChildren().add(new Label("加载中…"));
        showPage(reviewPage);

        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.SECOND_HAND_PENDING_LIST, null, null);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    reviewListContainer.getChildren().clear();
                    if (response != null && response.getCode() == ResponseCode.SUCCESS
                            && response.getData() instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<SecondHandVO> items = (List<SecondHandVO>) response.getData();
                        if (items == null || items.isEmpty()) {
                            Label empty = new Label("暂无待审核商品");
                            empty.getStyleClass().add("lib-subtitle");
                            reviewListContainer.getChildren().add(empty);
                        } else {
                            for (SecondHandVO item : items) {
                                reviewListContainer.getChildren().add(buildReviewRow(item));
                            }
                        }
                    } else {
                        reviewListContainer.getChildren().add(new Label(errorText(response, "加载失败")));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (reviewListContainer != null) {
                        reviewListContainer.getChildren().clear();
                        reviewListContainer.getChildren().add(new Label("网络错误: " + e.getMessage()));
                    }
                });
            }
        });
    }

    /**
     * 构建单条待审核商品行（含通过/拒绝按钮）。
     */
    private Node buildReviewRow(SecondHandVO item) {
        HBox row = new HBox(12.0);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("profile-card");
        row.setPadding(new Insets(10.0));

        // 缩略图（无图时显示占位图），便于管理员结合图片审核
        StackPane thumbBox = wrapThumbnail(createItemThumbnail(item, 100.0, 72.0), 100.0, 72.0);

        VBox infoBox = new VBox(3.0);
        Label title = new Label(item.getTitle() == null ? "" : item.getTitle());
        title.getStyleClass().add("shop-card-name");
        title.setWrapText(true);
        Label meta = new Label("卖家 " + (item.getSellerName() == null ? "" : item.getSellerName())
                + " · " + formatPrice(item.getPrice())
                + (item.getDescription() == null || item.getDescription().isEmpty()
                        ? "" : " · " + item.getDescription()));
        meta.getStyleClass().add("lib-subtitle");
        meta.setWrapText(true);
        infoBox.getChildren().addAll(title, meta);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button approveBtn = new Button("通过");
        approveBtn.getStyleClass().add("btn-primary-action");
        approveBtn.setOnAction(e -> doReview(item.getId(), true, row));

        Button rejectBtn = new Button("拒绝");
        rejectBtn.getStyleClass().add("lib-btn-danger");
        rejectBtn.setOnAction(e -> doReview(item.getId(), false, row));

        row.getChildren().addAll(thumbBox, infoBox, spacer, approveBtn, rejectBtn);
        return row;
    }

    /**
     * 提交审核结果（通过/拒绝）。
     */
    private void doReview(Integer id, boolean approve, Node row) {
        THREAD_POOL.execute(() -> {
            try {
                SecondHandVO vo = new SecondHandVO();
                vo.setId(id);
                vo.setStatus(approve ? "APPROVE" : "REJECT");
                Message request = new Message(currentUser.getAccountNumber(), MessageType.SECOND_HAND_REVIEW, null, vo);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS) {
                        if (reviewListContainer != null) {
                            reviewListContainer.getChildren().remove(row);
                            if (reviewListContainer.getChildren().isEmpty()) {
                                Label empty = new Label("已处理完毕，暂无待审核商品");
                                empty.getStyleClass().add("lib-subtitle");
                                reviewListContainer.getChildren().add(empty);
                            }
                        }
                        showToast(approve ? "已通过该商品" : "已拒绝该商品", ToastType.SUCCESS);
                        refresh();
                    } else {
                        showToast("审核失败：" + errorText(response, "审核失败，请稍后重试"), ToastType.ERROR);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showToast("网络错误：" + e.getMessage(), ToastType.ERROR));
            }
        });
    }

    /**
     * 我的发布入口：进入「我的发布」页面，加载我发布过的商品。
     */
    private void openMyListPage() {
        if (myListContainer == null) {
            return;
        }
        myListContainer.getChildren().clear();
        myListContainer.getChildren().add(new Label("加载中…"));
        showPage(myListPage);

        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.SECOND_HAND_MY_LIST, null, null);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    myListContainer.getChildren().clear();
                    if (response != null && response.getCode() == ResponseCode.SUCCESS
                            && response.getData() instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<SecondHandVO> items = (List<SecondHandVO>) response.getData();
                        if (items == null || items.isEmpty()) {
                            Label empty = new Label("你还没有发布过商品");
                            empty.getStyleClass().add("lib-subtitle");
                            myListContainer.getChildren().add(empty);
                        } else {
                            for (SecondHandVO item : items) {
                                myListContainer.getChildren().add(buildMyListRow(item));
                            }
                        }
                    } else {
                        myListContainer.getChildren().add(new Label(errorText(response, "加载失败")));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (myListContainer != null) {
                        myListContainer.getChildren().clear();
                        myListContainer.getChildren().add(new Label("网络错误: " + e.getMessage()));
                    }
                });
            }
        });
    }

    /**
     * 构建单条“我的发布”行（含状态徽标）。
     */
    private Node buildMyListRow(SecondHandVO item) {
        HBox row = new HBox(12.0);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("profile-card");
        row.setPadding(new Insets(10.0));

        // 缩略图（无图时显示占位图）
        StackPane thumbBox = wrapThumbnail(createItemThumbnail(item, 88.0, 62.0), 88.0, 62.0);

        VBox infoBox = new VBox(3.0);
        Label title = new Label(item.getTitle() == null ? "" : item.getTitle());
        title.getStyleClass().add("shop-card-name");
        Label meta = new Label("定价 " + formatPrice(item.getPrice())
                + " · 发布于 " + (item.getCreatedTime() == null ? "" : item.getCreatedTime()));
        meta.getStyleClass().add("lib-subtitle");
        infoBox.getChildren().addAll(title, meta);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 待审核 / 在售状态才允许换图（已售出、被拒绝的商品保留成交快照）
        boolean editable = "ON_SALE".equals(item.getStatus()) || "PENDING".equals(item.getStatus());
        boolean hasImage = item.getImage() != null && !item.getImage().isEmpty();

        Button changeImageBtn = new Button("更换图片");
        changeImageBtn.getStyleClass().add("btn-recharge-preset");
        changeImageBtn.setDisable(!editable);
        changeImageBtn.setOnAction(e -> chooseAndUpdateImage(item));

        Button removeImageBtn = new Button("移除图片");
        removeImageBtn.getStyleClass().add("lib-btn-danger");
        removeImageBtn.setDisable(!editable || !hasImage);
        removeImageBtn.setOnAction(e -> clearItemImage(item));

        HBox imageActions = new HBox(6.0, changeImageBtn, removeImageBtn);
        imageActions.setAlignment(Pos.CENTER_RIGHT);

        Label statusBadge = new Label(statusLabel(item.getStatus()));
        statusBadge.getStyleClass().addAll("shop-card-badge", statusBadgeClass(item.getStatus()));

        row.getChildren().addAll(thumbBox, infoBox, spacer, imageActions, statusBadge);
        return row;
    }

    private String errorText(Message response, String fallback) {
        if (response != null) {
            if (response.getData() instanceof String) {
                return (String) response.getData();
            }
            ResponseCode code = response.getCode();
            if (code == ResponseCode.SECOND_HAND_SOLD) {
                return "该商品已被买走或已下架";
            }
            if (code == ResponseCode.BALANCE_INSUFFICIENT) {
                return "校园卡余额不足，请先充值";
            }
            if (code == ResponseCode.INVALID_REQUEST) {
                return "不能购买自己发布的商品";
            }
        }
        return fallback;
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) {
            return "¥ 0.00";
        }
        return "¥ " + price.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 消息条类型（颜色与图标）。
     */
    public enum ToastType {
        /** 绿色：成功/提示 */
        SUCCESS,
        /** 蓝色：信息/通知 */
        INFO,
        /** 黄色：警告/注意 */
        WARNING,
        /** 红色：错误/失败 */
        ERROR
    }

    /**
     * 在面板顶部显示一条非阻塞提示（替代 Alert 弹窗），3 秒后自动消失。
     */
    private void showToast(String content, ToastType type) {
        if (toastBar == null || toastLabel == null) {
            return;
        }
        if (toastTimer != null) {
            toastTimer.stop();
        }
        toastLabel.setText(content == null ? "" : content);
        toastBar.getStyleClass().removeAll(
                "secondhand-toast-success", "secondhand-toast-info",
                "secondhand-toast-warning", "secondhand-toast-error");
        String styleClass = switch (type) {
            case SUCCESS -> "secondhand-toast-success";
            case WARNING -> "secondhand-toast-warning";
            case ERROR -> "secondhand-toast-error";
            default -> "secondhand-toast-info";
        };
        toastBar.getStyleClass().add(styleClass);
        toastBar.setVisible(true);
        toastBar.setManaged(true);
        toastTimer = new PauseTransition(Duration.seconds(3.0));
        toastTimer.setOnFinished(e -> hideToast());
        toastTimer.play();
    }

    private void hideToast() {
        if (toastBar == null) {
            return;
        }
        toastBar.setVisible(false);
        toastBar.setManaged(false);
        if (toastTimer != null) {
            toastTimer.stop();
        }
    }

    /** 简单占位：发布按钮图标（复用超市风格） */
    private static final class SvgIconsPlaceholder {
        static Node plus() {
            return com.vcampus.client.util.SvgIcons.createIcon("plus", 14.0, "shop-buy-icon");
        }
    }
}
