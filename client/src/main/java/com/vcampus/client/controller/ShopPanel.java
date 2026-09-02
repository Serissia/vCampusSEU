package com.vcampus.client.controller;

import com.vcampus.client.net.SocketClient;
import com.vcampus.client.util.SvgIcons;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.GoodsVO;
import com.vcampus.common.vo.OrderVO;
import com.vcampus.common.vo.UserRole;
import com.vcampus.common.vo.UserVO;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
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
 * 校园超市面板。
 *
 * <p>所有登录用户均可浏览商品、购买、查看订单、充值并查询余额。卖家
 * （SELLER / ADMIN）额外获得商品管理工具栏（新增、修改、删除），管理员还
 * 可强制下架商品。已下架商品对所有用户标记为“已下架”，无法购买。</p>
 *
 * <p>全部交互通过 {@link MessageType} 中的报文与服务端通信；商品管理请求
 * （{@code GOODS_ADD / GOODS_UPDATE / GOODS_DELETE / GOODS_OFF_SHELF}）由
 * 服务端校验角色权限，结账扣款与扣库存由服务端在同一事务内原子完成。</p>
 *
 * @author vCampus Team
 */
public class ShopPanel extends VBox {

    /**
     * 业务线程池，避免阻塞 JavaFX UI 线程。
     */
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
                    Thread thread = new Thread(r, "Shop-Thread-" + threadNumber.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private final SocketClient socketClient = new SocketClient();

    private UserVO currentUser;
    private MainController mainController;

    /** 是否为商品管理者（SELLER / ADMIN） */
    private boolean managerMode;
    /** 是否为管理员（可强制下架） */
    private boolean adminMode;

    private Label subtitleLabel;
    private Label balanceValueLabel;
    private HBox rechargeRow;
    private VBox bottomBar;
    private TextField rechargeField;
    private TextField searchField;
    private TableView<GoodsVO> goodsTable;
    private Spinner<Integer> quantitySpinner;
    private Button buyBtn;

    public ShopPanel() {
        buildUi();
    }

    /**
     * 注入用户上下文与主框架控制器引用，并按角色加载对应视图。
     *
     * @param user           当前登录用户
     * @param mainController 父级主控制器（用于同步顶部余额）
     */
    public void initData(UserVO user, MainController mainController) {
        this.currentUser = user;
        this.mainController = mainController;
        applyRoleMode();
        refreshGoods("");
        refreshBalance();
    }

    /**
     * 程序化构建面板整体布局。
     */
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

        bottomBar = new VBox(12.0);

        getChildren().addAll(buildTopCard(), buildTableCard(), bottomBar);
    }

    /**
     * 根据当前用户角色组装底部操作区。
     */
    private void applyRoleMode() {
        UserRole role = currentUser != null ? currentUser.getRole() : null;
        managerMode = role == UserRole.SELLER || role == UserRole.ADMIN;
        adminMode = role == UserRole.ADMIN;

        if (subtitleLabel != null) {
            if (adminMode) {
                subtitleLabel.setText("管理员：可购买商品，也可强制下架商品");
            } else if (managerMode) {
                subtitleLabel.setText("卖家工作台：可购买商品，也可新增、修改、删除商品");
            } else {
                subtitleLabel.setText("使用校园卡余额购买东大文创与日常用品");
            }
        }

        // 所有登录用户均可购买与充值，底部固定展示购买栏
        if (bottomBar != null) {
            bottomBar.getChildren().clear();
            bottomBar.getChildren().add(buildBuyerBar());
            if (managerMode) {
                bottomBar.getChildren().add(buildManagerBar());
            }
        }
    }

    /**
     * 顶部卡片：标题 + 余额徽标 + 在线充值区（所有登录用户可见）。
     */
    private Node buildTopCard() {
        VBox card = new VBox(14.0);
        card.getStyleClass().add("profile-card");

        HBox headerRow = new HBox(12.0);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4.0);
        Label title = new Label("校园超市");
        title.getStyleClass().add("lib-title");
        subtitleLabel = new Label("使用校园卡余额购买东大文创与日常用品");
        subtitleLabel.getStyleClass().add("lib-subtitle");
        titleBox.getChildren().addAll(title, subtitleLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox balanceBox = new VBox(2.0);
        balanceBox.setAlignment(Pos.CENTER_RIGHT);
        Label balanceCaption = new Label("校园卡余额");
        balanceCaption.getStyleClass().add("shop-balance-label");
        balanceValueLabel = new Label("¥ 0.00");
        balanceValueLabel.getStyleClass().add("shop-balance-value");
        balanceBox.getChildren().addAll(balanceCaption, balanceValueLabel);

        headerRow.getChildren().addAll(titleBox, spacer, balanceBox);

        rechargeRow = new HBox(10.0);
        rechargeRow.setAlignment(Pos.CENTER_LEFT);
        Label rechargeCaption = new Label("在线充值:");
        rechargeCaption.getStyleClass().add("shop-recharge-label");

        Button r50 = buildRechargePreset("+ ¥50", new BigDecimal("50.00"));
        Button r100 = buildRechargePreset("+ ¥100", new BigDecimal("100.00"));
        Button r200 = buildRechargePreset("+ ¥200", new BigDecimal("200.00"));

        rechargeField = new TextField();
        rechargeField.setPromptText("自定义金额");
        rechargeField.setPrefWidth(120.0);
        rechargeField.getStyleClass().add("modern-input-field");

        Button rechargeBtn = new Button("充值");
        rechargeBtn.getStyleClass().add("btn-primary-action");
        rechargeBtn.setOnAction(e -> handleCustomRecharge());

        rechargeRow.getChildren().addAll(rechargeCaption, r50, r100, r200, rechargeField, rechargeBtn);
        card.getChildren().addAll(headerRow, rechargeRow);
        return card;
    }

    /**
     * 中部卡片：商品列表表格。
     */
    private Node buildTableCard() {
        VBox card = new VBox(12.0);
        card.getStyleClass().add("profile-card");
        VBox.setVgrow(card, Priority.ALWAYS);

        Label sectionTitle = new Label("商品列表");
        sectionTitle.getStyleClass().add("lib-section-title");

        goodsTable = new TableView<>();
        goodsTable.getStyleClass().add("lib-table");
        goodsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(goodsTable, Priority.ALWAYS);
        setupGoodsTable();

        card.getChildren().addAll(sectionTitle, goodsTable);
        return card;
    }

    /**
     * 构造商品表格列。
     */
    private void setupGoodsTable() {
        TableColumn<GoodsVO, String> idCol = new TableColumn<>("商品编号");
        idCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getGoodsId()));
        idCol.setPrefWidth(110);

        TableColumn<GoodsVO, String> nameCol = new TableColumn<>("商品名称");
        nameCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getGoodsName()));
        nameCol.setPrefWidth(190);

        TableColumn<GoodsVO, String> priceCol = new TableColumn<>("单价");
        priceCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(formatPrice(cd.getValue().getPrice())));
        priceCol.setPrefWidth(90);

        TableColumn<GoodsVO, String> stockCol = new TableColumn<>("库存");
        stockCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(String.valueOf(cd.getValue().getStock())));
        stockCol.setPrefWidth(70);

        TableColumn<GoodsVO, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getStatus()));
        statusCol.setCellFactory(col -> new TableCell<GoodsVO, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().removeAll("status-on-shelf", "status-off-shelf");
                } else {
                    boolean onShelf = "ON_SHELF".equals(item);
                    setText(onShelf ? "上架" : "已下架");
                    getStyleClass().removeAll("status-on-shelf", "status-off-shelf");
                    getStyleClass().add(onShelf ? "status-on-shelf" : "status-off-shelf");
                }
            }
        });
        statusCol.setPrefWidth(70);

        TableColumn<GoodsVO, String> descCol = new TableColumn<>("商品描述");
        descCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                cd.getValue().getDescription() == null ? "" : cd.getValue().getDescription()));
        descCol.setPrefWidth(240);

        goodsTable.getColumns().addAll(idCol, nameCol, priceCol, stockCol, statusCol, descCol);
        for (TableColumn<GoodsVO, ?> col : goodsTable.getColumns()) {
            col.setMinWidth(60.0);
        }
    }

    /**
     * 购买栏（所有登录用户可见）：搜索框 + 购买数量 + 立即购买。
     */
    private Node buildBuyerBar() {
        HBox bar = new HBox(12.0);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("profile-card");

        searchField = new TextField();
        searchField.setPromptText("搜索商品名称 / 编号");
        searchField.getStyleClass().add("modern-input-field");
        searchField.setOnAction(e -> handleSearch());
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Button searchBtn = new Button("检索");
        searchBtn.getStyleClass().add("btn-primary-action");
        searchBtn.setOnAction(e -> handleSearch());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label qtyLabel = new Label("购买数量");
        qtyLabel.getStyleClass().add("shop-form-label");

        quantitySpinner = new Spinner<>(1, 99, 1);
        quantitySpinner.setPrefWidth(80.0);
        quantitySpinner.setEditable(true);

        buyBtn = new Button("立即购买");
        buyBtn.getStyleClass().add("shop-btn-buy");
        buyBtn.setGraphic(SvgIcons.createIcon("cart-shopping", 14.0, "shop-buy-icon"));
        buyBtn.setOnAction(e -> handleBuy());

        // 选中已下架商品时禁用购买
        goodsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            buyBtn.setDisable(newVal != null && "OFF_SHELF".equals(newVal.getStatus()));
        });

        bar.getChildren().addAll(searchField, searchBtn, spacer, qtyLabel, quantitySpinner, buyBtn);
        return bar;
    }

    /**
     * 商品管理栏（仅 SELLER / ADMIN 可见）：新增、修改、删除，管理员额外可强制下架。
     */
    private Node buildManagerBar() {
        HBox bar = new HBox(12.0);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("profile-card");

        Label caption = new Label("商品管理:");
        caption.getStyleClass().add("shop-recharge-label");

        Button addBtn = new Button("新增商品");
        addBtn.getStyleClass().add("btn-primary-action");
        addBtn.setGraphic(SvgIcons.createIcon("plus", 14.0, "shop-buy-icon"));
        addBtn.setOnAction(e -> handleAddGoods());

        Button editBtn = new Button("修改商品");
        editBtn.getStyleClass().add("btn-recharge-preset");
        editBtn.setOnAction(e -> handleEditGoods());

        Button deleteBtn = new Button("删除商品");
        deleteBtn.getStyleClass().add("lib-btn-danger");
        deleteBtn.setOnAction(e -> handleDeleteGoods());

        bar.getChildren().addAll(caption, addBtn, editBtn, deleteBtn);

        if (adminMode) {
            Button offShelfBtn = new Button("强制下架");
            offShelfBtn.getStyleClass().add("lib-btn-danger");
            offShelfBtn.setOnAction(e -> handleOffShelf());
            bar.getChildren().add(offShelfBtn);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label hint = new Label(adminMode
                ? "选中商品后可修改、删除或强制下架"
                : "选中商品后可修改或删除");
        hint.getStyleClass().add("lib-subtitle");
        bar.getChildren().addAll(spacer, hint);
        return bar;
    }

    /**
     * 触发关键字检索。
     */
    private void handleSearch() {
        String keyword = searchField.getText() == null ? "" : searchField.getText().trim();
        refreshGoods(keyword);
    }

    /**
     * 异步检索商品。
     */
    private void refreshGoods(String keyword) {
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.GOODS_QUERY, null, keyword);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS
                            && response.getData() instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<GoodsVO> goods = (List<GoodsVO>) response.getData();
                        goodsTable.getItems().setAll(goods);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("网络错误", "无法连接服务器: " + e.getMessage(), Alert.AlertType.ERROR));
            }
        });
    }

    /**
     * 异步刷新一卡通余额并同步主框架顶部余额。
     */
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
                        updateBalanceDisplay();
                        if (mainController != null) {
                            mainController.updateBalance(fresh.getBalance());
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("网络错误", "无法连接服务器: " + e.getMessage(), Alert.AlertType.ERROR));
            }
        });
    }

    /**
     * 处理自定义金额充值输入。
     */
    private void handleCustomRecharge() {
        String text = rechargeField.getText() == null ? "" : rechargeField.getText().trim();
        if (text.isEmpty()) {
            showAlert("提示", "请输入充值金额", Alert.AlertType.WARNING);
            return;
        }
        try {
            BigDecimal amount = new BigDecimal(text);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                showAlert("提示", "充值金额必须大于 0", Alert.AlertType.WARNING);
                return;
            }
            rechargeField.clear();
            recharge(amount);
        } catch (NumberFormatException e) {
            showAlert("错误", "请输入合法的数字金额", Alert.AlertType.ERROR);
        }
    }

    /**
     * 构造快捷充值按钮。
     */
    private Button buildRechargePreset(String text, BigDecimal amount) {
        Button btn = new Button(text);
        btn.getStyleClass().add("btn-recharge-preset");
        btn.setOnAction(e -> recharge(amount));
        return btn;
    }

    /**
     * 执行在线充值（异步发起 PAYMENT_RECHARGE 请求）。
     */
    private void recharge(BigDecimal amount) {
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.PAYMENT_RECHARGE, null, amount);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS
                            && response.getData() instanceof UserVO) {
                        UserVO fresh = (UserVO) response.getData();
                        currentUser.setBalance(fresh.getBalance());
                        updateBalanceDisplay();
                        if (mainController != null) {
                            mainController.updateBalance(fresh.getBalance());
                        }
                        showAlert("充值成功",
                                "成功充值 ¥ " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
                                        + "，当前余额 ¥ " + fresh.getBalance().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                                Alert.AlertType.INFORMATION);
                    } else {
                        String errMsg = (response != null && response.getData() instanceof String)
                                ? (String) response.getData() : "充值请求被服务器拒绝";
                        showAlert("充值失败", errMsg, Alert.AlertType.ERROR);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("网络错误", "无法连接服务器，充值失败: " + e.getMessage(), Alert.AlertType.ERROR));
            }
        });
    }

    /**
     * 处理购买：校验选择与数量后异步发起 ORDER_CREATE 请求。
     */
    private void handleBuy() {
        GoodsVO selected = goodsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("提示", "请先在商品列表中选择要购买的商品", Alert.AlertType.WARNING);
            return;
        }
        if ("OFF_SHELF".equals(selected.getStatus())) {
            showAlert("提示", "该商品已下架，无法购买", Alert.AlertType.WARNING);
            return;
        }
        int count = quantitySpinner.getValue() == null ? 1 : quantitySpinner.getValue();
        if (count <= 0) {
            showAlert("提示", "购买数量必须大于 0", Alert.AlertType.WARNING);
            return;
        }

        OrderVO order = new OrderVO();
        order.setStudentId(currentUser.getAccountNumber());
        order.setGoodsId(selected.getGoodsId());
        order.setCount(count);

        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.ORDER_CREATE, null, order);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS) {
                        OrderVO created = response.getData() instanceof OrderVO ? (OrderVO) response.getData() : order;
                        showAlert("购买成功", buildOrderSuccessMessage(created), Alert.AlertType.INFORMATION);
                        refreshGoods(searchField.getText() == null ? "" : searchField.getText().trim());
                        refreshBalance();
                    } else {
                        showAlert("购买失败", translateError(response), Alert.AlertType.ERROR);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("网络错误", "无法连接服务器，购买失败: " + e.getMessage(), Alert.AlertType.ERROR));
            }
        });
    }

    /**
     * 新增商品：弹出录入对话框后提交 GOODS_ADD 请求。
     */
    private void handleAddGoods() {
        GoodsVO draft = new GoodsVO();
        if (showGoodsDialog("新增商品", draft, true)) {
            submitGoods(MessageType.GOODS_ADD, draft, "新增商品成功");
        }
    }

    /**
     * 修改商品：对选中商品弹出编辑对话框后提交 GOODS_UPDATE 请求。
     */
    private void handleEditGoods() {
        GoodsVO selected = goodsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("提示", "请先在商品列表中选择要修改的商品", Alert.AlertType.WARNING);
            return;
        }
        GoodsVO draft = new GoodsVO();
        draft.setGoodsId(selected.getGoodsId());
        draft.setGoodsName(selected.getGoodsName());
        draft.setPrice(selected.getPrice());
        draft.setStock(selected.getStock());
        draft.setDescription(selected.getDescription());
        if (showGoodsDialog("修改商品", draft, false)) {
            submitGoods(MessageType.GOODS_UPDATE, draft, "修改商品成功");
        }
    }

    /**
     * 删除商品：确认后提交 GOODS_DELETE 请求。
     */
    private void handleDeleteGoods() {
        GoodsVO selected = goodsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("提示", "请先在商品列表中选择要删除的商品", Alert.AlertType.WARNING);
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认删除");
        confirm.setHeaderText(null);
        confirm.setContentText("确定要删除商品 [" + selected.getGoodsName() + "] 吗？");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            submitGoods(MessageType.GOODS_DELETE, selected, "删除商品成功");
        }
    }

    /**
     * 强制下架商品：仅管理员可见该按钮，确认后提交 GOODS_OFF_SHELF 请求。
     */
    private void handleOffShelf() {
        GoodsVO selected = goodsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("提示", "请先在商品列表中选择要下架的商品", Alert.AlertType.WARNING);
            return;
        }
        if ("OFF_SHELF".equals(selected.getStatus())) {
            showAlert("提示", "该商品已下架", Alert.AlertType.INFORMATION);
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认下架");
        confirm.setHeaderText(null);
        confirm.setContentText("确定强制下架商品 [" + selected.getGoodsName() + "] 吗？下架后所有用户将无法购买。");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            submitGoods(MessageType.GOODS_OFF_SHELF, selected, "商品已强制下架");
        }
    }

    /**
     * 商品管理弹窗：新增时编号可编辑，修改时编号只读。
     *
     * @return true 表示用户点击保存且校验通过，数据已回填至 goods
     */
    private boolean showGoodsDialog(String title, GoodsVO goods, boolean isAdd) {
        Dialog<GoodsVO> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(isAdd ? "录入新商品信息" : "修改商品信息");
        dialog.getDialogPane().getStylesheets().addAll(getStylesheets());

        ButtonType saveType = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField idField = new TextField(goods.getGoodsId() == null ? "" : goods.getGoodsId());
        idField.setPromptText("商品编号，如 G004");
        idField.setEditable(isAdd);
        TextField nameField = new TextField(goods.getGoodsName() == null ? "" : goods.getGoodsName());
        nameField.setPromptText("商品名称");
        TextField priceField = new TextField(goods.getPrice() == null ? "" : goods.getPrice().toPlainString());
        priceField.setPromptText("售价，如 15.00");
        TextField stockField = new TextField(String.valueOf(goods.getStock()));
        stockField.setPromptText("库存数量");
        TextField descField = new TextField(goods.getDescription() == null ? "" : goods.getDescription());
        descField.setPromptText("商品描述（可选）");

        GridPane grid = new GridPane();
        grid.setHgap(10.0);
        grid.setVgap(10.0);
        grid.setPadding(new Insets(20.0));
        grid.add(new Label("商品编号"), 0, 0);
        grid.add(idField, 1, 0);
        grid.add(new Label("商品名称"), 0, 1);
        grid.add(nameField, 1, 1);
        grid.add(new Label("售价"), 0, 2);
        grid.add(priceField, 1, 2);
        grid.add(new Label("库存"), 0, 3);
        grid.add(stockField, 1, 3);
        grid.add(new Label("描述"), 0, 4);
        grid.add(descField, 1, 4);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveType) {
                GoodsVO result = new GoodsVO();
                result.setGoodsId(idField.getText().trim());
                result.setGoodsName(nameField.getText().trim());
                try {
                    result.setPrice(new BigDecimal(priceField.getText().trim()));
                } catch (NumberFormatException e) {
                    result.setPrice(null);
                }
                try {
                    result.setStock(Integer.parseInt(stockField.getText().trim()));
                } catch (NumberFormatException e) {
                    result.setStock(-1);
                }
                result.setDescription(descField.getText().trim());
                return result;
            }
            return null;
        });

        Optional<GoodsVO> result = dialog.showAndWait();
        if (result.isPresent()) {
            GoodsVO value = result.get();
            if (value.getGoodsId().isEmpty() || value.getGoodsName().isEmpty()
                    || value.getPrice() == null || value.getStock() < 0) {
                showAlert("提示", "请完整填写商品编号、名称、售价与库存（数字）", Alert.AlertType.WARNING);
                return false;
            }
            if (value.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                showAlert("提示", "售价不能为负数", Alert.AlertType.WARNING);
                return false;
            }
            goods.setGoodsId(value.getGoodsId());
            goods.setGoodsName(value.getGoodsName());
            goods.setPrice(value.getPrice());
            goods.setStock(value.getStock());
            goods.setDescription(value.getDescription());
            return true;
        }
        return false;
    }

    /**
     * 异步提交商品管理请求（新增 / 修改 / 删除 / 强制下架）。
     */
    private void submitGoods(MessageType type, GoodsVO goods, String successMsg) {
        THREAD_POOL.execute(() -> {
            try {
                Object payload = (type == MessageType.GOODS_DELETE || type == MessageType.GOODS_OFF_SHELF)
                        ? goods.getGoodsId() : goods;
                Message request = new Message(currentUser.getAccountNumber(), type, null, payload);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS) {
                        showAlert("操作成功", successMsg, Alert.AlertType.INFORMATION);
                        refreshGoods(searchField.getText() == null ? "" : searchField.getText().trim());
                    } else if (response != null && response.getCode() == ResponseCode.UNAUTHORIZED) {
                        showAlert("无权限", "当前账号无权执行该操作", Alert.AlertType.ERROR);
                    } else {
                        String errMsg = (response != null && response.getData() instanceof String)
                                ? (String) response.getData() : "操作失败，请稍后重试";
                        showAlert("操作失败", errMsg, Alert.AlertType.ERROR);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("网络错误", "无法连接服务器: " + e.getMessage(), Alert.AlertType.ERROR));
            }
        });
    }

    /**
     * 组装购买成功提示内容。
     */
    private String buildOrderSuccessMessage(OrderVO order) {
        StringBuilder sb = new StringBuilder();
        sb.append("订单号: ").append(order.getOrderId() == null ? "--" : order.getOrderId()).append('\n');
        sb.append("商品: ").append(order.getGoodsName()).append(" × ").append(order.getCount()).append('\n');
        sb.append("实付金额: ¥ ").append(formatPrice(order.getTotalPrice())).append('\n');
        sb.append("下单时间: ").append(order.getOrderTime() == null ? "--" : order.getOrderTime());
        return sb.toString();
    }

    /**
     * 将失败响应转换为用户可读的错误信息。
     */
    private String translateError(Message response) {
        if (response == null) {
            return "服务器无响应";
        }
        if (response.getData() instanceof String) {
            return (String) response.getData();
        }
        ResponseCode code = response.getCode();
        if (code == ResponseCode.GOODS_NOT_FOUND) {
            return "商品不存在或已下架，请刷新后重试";
        }
        if (code == ResponseCode.GOODS_STOCK_INSUFFICIENT) {
            return "商品库存不足，请调整购买数量";
        }
        if (code == ResponseCode.BALANCE_INSUFFICIENT) {
            return "校园卡余额不足，请先充值";
        }
        if (code == ResponseCode.INVALID_REQUEST) {
            return "购买请求参数不合法";
        }
        return "操作失败，请稍后重试";
    }

    /**
     * 刷新余额展示。
     */
    private void updateBalanceDisplay() {
        BigDecimal balance = currentUser != null && currentUser.getBalance() != null
                ? currentUser.getBalance() : BigDecimal.ZERO;
        balanceValueLabel.setText("¥ " + balance.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    /**
     * 格式化金额显示。
     */
    private String formatPrice(BigDecimal price) {
        if (price == null) {
            return "¥ 0.00";
        }
        return "¥ " + price.setScale(2, RoundingMode.HALF_UP).toPlainString();
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