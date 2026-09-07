package com.vcampus.client.controller;

import com.vcampus.client.net.SocketClient;
import com.vcampus.client.util.ScrollSpeedUtil;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.SecondHandVO;
import com.vcampus.common.vo.UserRole;
import com.vcampus.common.vo.UserVO;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
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

    private final SocketClient socketClient = new SocketClient();

    private UserVO currentUser;
    private MainController mainController;

    private Label balanceValueLabel;
    /** 作为校园超市二级页时的返回回调（null 表示独立使用，不显示返回） */
    private Runnable onBack;
    private Button backBtn;
    private FlowPane cardFlowPane;
    /** 管理员审核入口按钮（仅 ADMIN 可见） */
    private Button reviewBtn;

    /** 多页面宿主：listings/review/myList/publish/confirmBuy/confirmOffShelf */
    private StackPane pageHost;
    private VBox listingsPage;
    private VBox reviewPage;
    private VBox myListPage;
    private VBox publishPage;
    private VBox confirmBuyPage;
    private VBox confirmOffShelfPage;
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
    /** 审核页/我的发布页 的列表容器（异步填充） */
    private VBox reviewListContainer;
    private VBox myListContainer;

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

        // 页面宿主：StackPane 互斥显示
        pageHost = new StackPane();
        VBox.setVgrow(pageHost, Priority.ALWAYS);
        pageHost.getChildren().addAll(listingsPage, reviewPage, myListPage,
                publishPage, confirmBuyPage, confirmOffShelfPage);
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
        boolean inTopLevel = (currentPage == listingsPage);
        boolean show = !inTopLevel || onBack != null;
        backBtn.setVisible(show);
        backBtn.setManaged(show);
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

        VBox titleBox = new VBox(4.0);
        Label title = new Label("二手市场");
        title.getStyleClass().add("lib-title");
        Label subtitle = new Label("学生闲置好物，上架你的旧物或淘到心仪宝贝");
        subtitle.getStyleClass().add("lib-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox balanceBox = new VBox(2.0);
        balanceBox.setAlignment(Pos.CENTER_RIGHT);
        Label balanceCaption = new Label("校园卡余额");
        balanceCaption.getStyleClass().add("shop-balance-label");
        balanceValueLabel = new Label("¥ 0.00");
        balanceValueLabel.getStyleClass().add("shop-balance-value");
        balanceBox.getChildren().addAll(balanceCaption, balanceValueLabel);

        Button myListBtn = new Button("我的发布");
        myListBtn.getStyleClass().add("btn-recharge-preset");
        myListBtn.setOnAction(e -> openMyListPage());

        reviewBtn = new Button("审核");
        reviewBtn.getStyleClass().add("btn-recharge-preset");
        reviewBtn.setVisible(false);
        reviewBtn.setManaged(false);
        reviewBtn.setOnAction(e -> openReviewPage());

        Button publishBtn = new Button("发布闲置");
        publishBtn.getStyleClass().add("btn-primary-action");
        publishBtn.setGraphic(SvgIconsPlaceholder.plus());
        publishBtn.setOnAction(e -> openPublishPage());

        Button refreshBtn = new Button("刷新");
        refreshBtn.getStyleClass().add("btn-recharge-preset");
        refreshBtn.setOnAction(e -> refresh());

        headerRow.getChildren().addAll(backBtn, titleBox, spacer, balanceBox, myListBtn, reviewBtn, publishBtn, refreshBtn);
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
        publish(vo);
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
    }

    /**
     * 刷新在售列表与余额。
     */
    /**
     * 设置返回回调（被校园超市当作二级页嵌入时调用）。
     */
    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
        updateBackButton();
    }

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

        // 顶部：状态徽标
        HBox topRow = new HBox(6.0);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label badge = new Label("在售");
        badge.getStyleClass().addAll("shop-card-badge", "shop-card-badge-on");
        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        Label seller = new Label("卖家 " + (item.getSellerName() == null ? "" : item.getSellerName()));
        seller.getStyleClass().add("lib-subtitle");
        topRow.getChildren().addAll(badge, topSpacer, seller);

        Label title = new Label(item.getTitle() == null ? "" : item.getTitle());
        title.getStyleClass().add("shop-card-name");
        title.setWrapText(true);

        Label desc = new Label(item.getDescription() == null ? "" : item.getDescription());
        desc.getStyleClass().add("shop-card-desc");
        desc.setWrapText(true);
        desc.setMaxHeight(44.0);
        desc.setMinHeight(34.0);
        VBox.setVgrow(desc, Priority.ALWAYS);

        Label price = new Label(formatPrice(item.getPrice()));
        price.getStyleClass().add("shop-card-price");

        boolean mine = currentUser != null && currentUser.getAccountNumber() != null
                && currentUser.getAccountNumber().equals(item.getSellerId());

        Button actionBtn = mine ? new Button("下架") : new Button("购买");
        actionBtn.setMaxWidth(Double.MAX_VALUE);
        actionBtn.setMinHeight(28.0);
        if (mine) {
            actionBtn.getStyleClass().add("lib-btn-danger");
            actionBtn.setOnAction(e -> confirmOffShelf(item));
        } else {
            actionBtn.getStyleClass().add("shop-btn-buy");
            actionBtn.setOnAction(e -> confirmBuy(item));
        }

        card.getChildren().addAll(topRow, title, desc, price, actionBtn);
        return card;
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
        summary.getChildren().addAll(tName, tSeller, tPrice);

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
        switch (status) {
            case "PENDING":
                return "待审核";
            case "ON_SALE":
                return "在售";
            case "SOLD":
                return "已售/已下架";
            case "REJECTED":
                return "审核未通过";
            default:
                return status;
        }
    }

    /**
     * 根据状态返回徽标样式类。
     */
    private String statusBadgeClass(String status) {
        switch (status == null ? "" : status) {
            case "ON_SALE":
                return "shop-card-badge-on";
            case "PENDING":
                return "shop-card-badge-pending";
            case "REJECTED":
                return "shop-card-badge-rejected";
            case "SOLD":
            default:
                return "shop-card-badge-off";
        }
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
        HBox row = new HBox(10.0);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("profile-card");
        row.setPadding(new Insets(10.0));

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

        row.getChildren().addAll(infoBox, spacer, approveBtn, rejectBtn);
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
        HBox row = new HBox(10.0);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("profile-card");
        row.setPadding(new Insets(10.0));

        VBox infoBox = new VBox(3.0);
        Label title = new Label(item.getTitle() == null ? "" : item.getTitle());
        title.getStyleClass().add("shop-card-name");
        Label meta = new Label("定价 " + formatPrice(item.getPrice())
                + " · 发布于 " + (item.getCreatedTime() == null ? "" : item.getCreatedTime()));
        meta.getStyleClass().add("lib-subtitle");
        infoBox.getChildren().addAll(title, meta);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusBadge = new Label(statusLabel(item.getStatus()));
        statusBadge.getStyleClass().addAll("shop-card-badge", statusBadgeClass(item.getStatus()));

        row.getChildren().addAll(infoBox, spacer, statusBadge);
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
        SUCCESS, INFO, WARNING, ERROR
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
        String styleClass;
        switch (type) {
            case SUCCESS: styleClass = "secondhand-toast-success"; break;
            case WARNING: styleClass = "secondhand-toast-warning"; break;
            case ERROR:   styleClass = "secondhand-toast-error";   break;
            default:      styleClass = "secondhand-toast-info";    break;
        }
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