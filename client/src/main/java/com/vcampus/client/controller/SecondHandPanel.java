package com.vcampus.client.controller;

import com.vcampus.client.net.SocketClient;
import com.vcampus.client.util.ScrollSpeedUtil;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.SecondHandVO;
import com.vcampus.common.vo.UserRole;
import com.vcampus.common.vo.UserVO;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
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

        getChildren().addAll(buildHeader(), buildGridCard());
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
        backBtn.setOnAction(e -> {
            if (onBack != null) {
                onBack.run();
            }
        });

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
        myListBtn.setOnAction(e -> openMyListDialog());

        reviewBtn = new Button("审核");
        reviewBtn.getStyleClass().add("btn-recharge-preset");
        reviewBtn.setVisible(false);
        reviewBtn.setManaged(false);
        reviewBtn.setOnAction(e -> openReviewDialog());

        Button publishBtn = new Button("发布闲置");
        publishBtn.getStyleClass().add("btn-primary-action");
        publishBtn.setGraphic(SvgIconsPlaceholder.plus());
        publishBtn.setOnAction(e -> showPublishDialog());

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
     * 刷新在售列表与余额。
     */
    /**
     * 设置返回回调（被校园超市当作二级页嵌入时调用）。
     */
    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
        if (backBtn != null) {
            backBtn.setVisible(true);
            backBtn.setManaged(true);
        }
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
                        showAlert("读取失败", errorText(response, "无法读取二手市场"), Alert.AlertType.ERROR);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("网络错误", "无法连接服务器: " + e.getMessage(), Alert.AlertType.ERROR));
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
    private void showPublishDialog() {
        Dialog<SecondHandVO> dialog = new Dialog<>();
        dialog.setTitle("发布闲置");
        dialog.setHeaderText("填写商品信息，发布后即可被同学看到并购买");
        dialog.getDialogPane().getStylesheets().addAll(getStylesheets());

        ButtonType saveType = new ButtonType("发布", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField titleField = new TextField();
        titleField.setPromptText("商品标题，如：计算机组成原理（任国林版）二手书");
        TextField priceField = new TextField();
        priceField.setPromptText("定价，如 20.00");
        TextArea descField = new TextArea();
        descField.setPromptText("描述（新旧程度、备注等，可选）");
        descField.setPrefRowCount(3);

        GridPane grid = new GridPane();
        grid.setHgap(10.0);
        grid.setVgap(10.0);
        grid.setPadding(new Insets(20.0));
        grid.add(new Label("标题"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("定价"), 0, 1);
        grid.add(priceField, 1, 1);
        grid.add(new Label("描述"), 0, 2);
        grid.add(descField, 1, 2);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == saveType) {
                SecondHandVO vo = new SecondHandVO();
                vo.setTitle(titleField.getText().trim());
                vo.setDescription(descField.getText().trim());
                try {
                    vo.setPrice(new BigDecimal(priceField.getText().trim()));
                } catch (NumberFormatException e) {
                    vo.setPrice(null);
                }
                return vo;
            }
            return null;
        });

        Optional<SecondHandVO> result = dialog.showAndWait();
        if (result.isPresent()) {
            SecondHandVO vo = result.get();
            if (vo.getTitle().isEmpty() || vo.getPrice() == null
                    || vo.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                showAlert("提示", "请填写标题与大于 0 的定价", Alert.AlertType.WARNING);
                return;
            }
            publish(vo);
        }
    }

    private void publish(SecondHandVO vo) {
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.SECOND_HAND_PUBLISH, null, vo);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS) {
                        showAlert("发布成功", "商品已提交，等待管理员审核通过后上架", Alert.AlertType.INFORMATION);
                        refresh();
                    } else {
                        showAlert("发布失败", errorText(response, "发布失败"), Alert.AlertType.ERROR);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("网络错误", "无法连接服务器: " + e.getMessage(), Alert.AlertType.ERROR));
            }
        });
    }

    private void confirmBuy(SecondHandVO item) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认购买");
        confirm.setHeaderText(null);
        Label content = new Label("确认购买「" + item.getTitle() + "」？\n将支付 "
                + formatPrice(item.getPrice()) + "，货款直接转入卖家账户，买下后该商品即下架。");
        content.setWrapText(true);
        content.setStyle("-fx-font-size: 13px;");
        confirm.getDialogPane().setContent(content);
        confirm.getDialogPane().setPrefWidth(460.0);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            doBuy(item);
        }
    }

    private void doBuy(SecondHandVO item) {
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.SECOND_HAND_BUY, null, item.getId());
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS) {
                        showAlert("购买成功", "已购入「" + item.getTitle() + "」，卖家将收到货款。", Alert.AlertType.INFORMATION);
                        refresh();
                    } else {
                        showAlert("购买失败", errorText(response, "购买失败"), Alert.AlertType.ERROR);
                        refreshListings();
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("网络错误", "无法连接服务器: " + e.getMessage(), Alert.AlertType.ERROR));
            }
        });
    }

    private void confirmOffShelf(SecondHandVO item) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认下架");
        confirm.setHeaderText(null);
        Label content = new Label("确定下架「" + item.getTitle() + "」吗？");
        content.setWrapText(true);
        content.setStyle("-fx-font-size: 13px;");
        confirm.getDialogPane().setContent(content);
        confirm.getDialogPane().setPrefWidth(400.0);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            THREAD_POOL.execute(() -> {
                try {
                    Message request = new Message(currentUser.getAccountNumber(), MessageType.SECOND_HAND_OFF_SHELF, null, item.getId());
                    Message response = socketClient.send(request);
                    Platform.runLater(() -> {
                        if (response != null && response.getCode() == ResponseCode.SUCCESS) {
                            refresh();
                        } else {
                            showAlert("下架失败", errorText(response, "下架失败"), Alert.AlertType.ERROR);
                        }
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showAlert("网络错误", "无法连接服务器: " + e.getMessage(), Alert.AlertType.ERROR));
                }
            });
        }
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
     * 管理员审核入口：弹出待审核商品列表，可逐条通过/拒绝。
     */
    private void openReviewDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("审核二手商品");
        dialog.setHeaderText("待审核的商品，通过后即可上架");
        dialog.getDialogPane().getStylesheets().addAll(getStylesheets());
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("关闭", ButtonBar.ButtonData.CANCEL_CLOSE));

        VBox listBox = new VBox(8.0);
        listBox.setPadding(new Insets(12.0));
        listBox.setPrefWidth(560.0);
        Label loading = new Label("加载中…");
        loading.getStyleClass().add("lib-subtitle");
        listBox.getChildren().add(loading);
        dialog.getDialogPane().setContent(listBox);
        dialog.getDialogPane().setPrefWidth(560.0);
        dialog.getDialogPane().setMinWidth(560.0);

        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.SECOND_HAND_PENDING_LIST, null, null);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    listBox.getChildren().clear();
                    if (response != null && response.getCode() == ResponseCode.SUCCESS
                            && response.getData() instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<SecondHandVO> items = (List<SecondHandVO>) response.getData();
                        if (items == null || items.isEmpty()) {
                            Label empty = new Label("暂无待审核商品");
                            empty.getStyleClass().add("lib-subtitle");
                            listBox.getChildren().add(empty);
                        } else {
                            for (SecondHandVO item : items) {
                                listBox.getChildren().add(buildReviewRow(item, listBox));
                            }
                        }
                    } else {
                        listBox.getChildren().add(new Label(errorText(response, "加载失败")));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    listBox.getChildren().clear();
                    listBox.getChildren().add(new Label("网络错误: " + e.getMessage()));
                });
            }
        });

        dialog.showAndWait();
    }

    /**
     * 构建单条待审核商品行（含通过/拒绝按钮）。
     */
    private Node buildReviewRow(SecondHandVO item, VBox listBox) {
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
        approveBtn.setOnAction(e -> doReview(item.getId(), true, row, listBox));

        Button rejectBtn = new Button("拒绝");
        rejectBtn.getStyleClass().add("lib-btn-danger");
        rejectBtn.setOnAction(e -> doReview(item.getId(), false, row, listBox));

        row.getChildren().addAll(infoBox, spacer, approveBtn, rejectBtn);
        return row;
    }

    /**
     * 提交审核结果（通过/拒绝）。
     */
    private void doReview(Integer id, boolean approve, Node row, VBox listBox) {
        THREAD_POOL.execute(() -> {
            try {
                SecondHandVO vo = new SecondHandVO();
                vo.setId(id);
                vo.setStatus(approve ? "APPROVE" : "REJECT");
                Message request = new Message(currentUser.getAccountNumber(), MessageType.SECOND_HAND_REVIEW, null, vo);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS) {
                        listBox.getChildren().remove(row);
                        if (listBox.getChildren().isEmpty()) {
                            Label empty = new Label("已处理完毕，暂无待审核商品");
                            empty.getStyleClass().add("lib-subtitle");
                            listBox.getChildren().add(empty);
                        }
                        refresh();
                    } else {
                        showAlert("审核失败", errorText(response, "审核失败，请稍后重试"), Alert.AlertType.ERROR);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("网络错误", "无法连接服务器: " + e.getMessage(), Alert.AlertType.ERROR));
            }
        });
    }

    /**
     * 我的发布入口：查看自己发布的商品及审核/交易状态。
     */
    private void openMyListDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("我的发布");
        dialog.setHeaderText("我发布的二手商品及状态");
        dialog.getDialogPane().getStylesheets().addAll(getStylesheets());
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("关闭", ButtonBar.ButtonData.CANCEL_CLOSE));

        VBox listBox = new VBox(8.0);
        listBox.setPadding(new Insets(12.0));
        Label loading = new Label("加载中…");
        loading.getStyleClass().add("lib-subtitle");
        listBox.getChildren().add(loading);
        dialog.getDialogPane().setContent(listBox);

        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.SECOND_HAND_MY_LIST, null, null);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    listBox.getChildren().clear();
                    if (response != null && response.getCode() == ResponseCode.SUCCESS
                            && response.getData() instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<SecondHandVO> items = (List<SecondHandVO>) response.getData();
                        if (items == null || items.isEmpty()) {
                            Label empty = new Label("你还没有发布过商品");
                            empty.getStyleClass().add("lib-subtitle");
                            listBox.getChildren().add(empty);
                        } else {
                            for (SecondHandVO item : items) {
                                listBox.getChildren().add(buildMyListRow(item));
                            }
                        }
                    } else {
                        listBox.getChildren().add(new Label(errorText(response, "加载失败")));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    listBox.getChildren().clear();
                    listBox.getChildren().add(new Label("网络错误: " + e.getMessage()));
                });
            }
        });

        dialog.showAndWait();
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

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        Label body = new Label(content);
        body.setWrapText(true);
        body.setStyle("-fx-font-size: 13px;");
        alert.getDialogPane().setContent(body);
        alert.getDialogPane().setPrefWidth(420.0);
        alert.showAndWait();
    }

    /** 简单占位：发布按钮图标（复用超市风格） */
    private static final class SvgIconsPlaceholder {
        static Node plus() {
            return com.vcampus.client.util.SvgIcons.createIcon("plus", 14.0, "shop-buy-icon");
        }
    }
}