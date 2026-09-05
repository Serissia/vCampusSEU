package com.vcampus.client.controller;

import com.vcampus.client.net.SocketClient;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.OrderVO;
import com.vcampus.common.vo.StatisticsVO;
import com.vcampus.common.vo.UserRole;
import com.vcampus.common.vo.UserVO;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 订单管理面板（仅 ADMIN / SELLER）：查看全部订单、按商品名称/用户 ID 筛选，
 * 并展示总订单数、总销售额与热门商品 Top3 统计。
 *
 * @author vCampus Team
 */
public class OrderManagementPanel extends VBox {

    private static final ExecutorService THREAD_POOL = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "OrderManage-Thread-" + threadNumber.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private final SocketClient socketClient = new SocketClient();

    private UserVO currentUser;

    private TextField goodsFilterField;
    private TextField userFilterField;
    private TableView<OrderVO> orderTable;
    private Label totalOrdersValue;
    private Label totalRevenueValue;
    private VBox topProductsBox;
    private Label tableCountLabel;

    /** 服务端返回的全量订单缓存，用于本地筛选 */
    private List<OrderVO> allOrders = new ArrayList<>();

    public OrderManagementPanel() {
        buildUi();
    }

    /**
     * 注入用户上下文；非 ADMIN/SELLER 拒绝加载数据。
     */
    public void initData(UserVO user) {
        this.currentUser = user;
        UserRole role = user != null ? user.getRole() : null;
        if (role != UserRole.ADMIN && role != UserRole.SELLER) {
            getChildren().clear();
            Label denied = new Label("无权访问订单管理（仅管理员/卖家可用）");
            denied.getStyleClass().add("orders-empty");
            getChildren().add(denied);
            return;
        }
        refreshAll();
    }

    /**
     * 构建面板布局。
     */
    private void buildUi() {
        String[] css = {"/css/tokens.css", "/css/base.css", "/css/library.css", "/css/orders.css"};
        for (String path : css) {
            java.net.URL url = getClass().getResource(path);
            if (url != null) {
                getStylesheets().add(url.toExternalForm());
            }
        }

        setSpacing(16.0);
        setPadding(new Insets(4.0));
        getStyleClass().add("orders-container");
        VBox.setVgrow(this, Priority.ALWAYS);

        // 顶部标题
        VBox header = new VBox(0.0);
        header.getStyleClass().add("profile-card");
        HBox headerRow = new HBox(12.0);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4.0);
        Label title = new Label("订单管理");
        title.getStyleClass().add("lib-title");
        Label subtitle = new Label("查看全部用户订单与销售统计（仅管理员 / 卖家）");
        subtitle.getStyleClass().add("lib-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("刷新");
        refreshBtn.getStyleClass().add("btn-primary-action");
        refreshBtn.setOnAction(e -> refreshAll());

        headerRow.getChildren().addAll(titleBox, spacer, refreshBtn);
        header.getChildren().add(headerRow);

        // 统计卡
        HBox statsRow = new HBox(40.0);
        statsRow.setAlignment(Pos.CENTER_LEFT);
        statsRow.getStyleClass().add("profile-card");

        VBox totalOrdersBox = new VBox(4.0);
        Label ordersCaption = new Label("总订单数");
        ordersCaption.getStyleClass().add("orders-stat-title");
        totalOrdersValue = new Label("0");
        totalOrdersValue.getStyleClass().add("orders-stat-value");
        totalOrdersBox.getChildren().addAll(ordersCaption, totalOrdersValue);

        VBox revenueBox = new VBox(4.0);
        Label revenueCaption = new Label("总销售额");
        revenueCaption.getStyleClass().add("orders-stat-title");
        totalRevenueValue = new Label("¥ 0.00");
        totalRevenueValue.getStyleClass().addAll("orders-stat-value", "accent");
        revenueBox.getChildren().addAll(revenueCaption, totalRevenueValue);

        VBox topBox = new VBox(4.0);
        Label topCaption = new Label("热门商品 Top 3");
        topCaption.getStyleClass().add("orders-stat-title");
        topProductsBox = new VBox(4.0);
        topBox.getChildren().addAll(topCaption, topProductsBox);

        statsRow.getChildren().addAll(totalOrdersBox, revenueBox, topBox);
        HBox.setHgrow(topBox, Priority.ALWAYS);

        // 筛选栏
        HBox filterRow = new HBox(10.0);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        filterRow.getStyleClass().add("profile-card");

        goodsFilterField = new TextField();
        goodsFilterField.setPromptText("按商品名称筛选");
        goodsFilterField.getStyleClass().add("modern-input-field");
        goodsFilterField.setPrefWidth(180.0);
        goodsFilterField.setOnAction(e -> applyFilters());

        userFilterField = new TextField();
        userFilterField.setPromptText("按用户 ID 筛选");
        userFilterField.getStyleClass().add("modern-input-field");
        userFilterField.setPrefWidth(160.0);
        userFilterField.setOnAction(e -> applyFilters());

        Button searchBtn = new Button("查询");
        searchBtn.getStyleClass().add("btn-primary-action");
        searchBtn.setOnAction(e -> applyFilters());

        Button resetBtn = new Button("重置");
        resetBtn.getStyleClass().add("btn-recharge-preset");
        resetBtn.setOnAction(e -> {
            goodsFilterField.clear();
            userFilterField.clear();
            applyFilters();
        });

        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);

        filterRow.getChildren().addAll(
                new Label("筛选："), goodsFilterField, userFilterField, searchBtn, resetBtn, filterSpacer);

        // 全部订单表格
        VBox tableCard = new VBox(12.0);
        tableCard.getStyleClass().add("profile-card");
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        HBox tableTitleRow = new HBox(12.0);
        tableTitleRow.setAlignment(Pos.CENTER_LEFT);
        Label sectionTitle = new Label("全部订单（双击查看详情）");
        sectionTitle.getStyleClass().add("lib-section-title");
        Region tableSpacer = new Region();
        HBox.setHgrow(tableSpacer, Priority.ALWAYS);
        tableCountLabel = new Label();
        tableCountLabel.getStyleClass().add("orders-summary");
        tableTitleRow.getChildren().addAll(sectionTitle, tableSpacer, tableCountLabel);

        orderTable = new TableView<>();
        orderTable.getStyleClass().add("lib-table");
        orderTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        // 在 ScrollPane 内保持固定高度、表格内部滚动        orderTable.setPrefHeight(440.0);
        setupTable();

        tableCard.getChildren().addAll(tableTitleRow, orderTable);

        getChildren().addAll(header, statsRow, filterRow, tableCard);
    }

    /**
     * 构造订单表格列（含用户 ID）。
     */
    private void setupTable() {
        TableColumn<OrderVO, String> orderIdCol = new TableColumn<>("订单号");
        orderIdCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getOrderId()));
        orderIdCol.setPrefWidth(180);

        TableColumn<OrderVO, String> userCol = new TableColumn<>("用户 ID");
        userCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getStudentId()));
        userCol.setPrefWidth(90);

        TableColumn<OrderVO, String> goodsNameCol = new TableColumn<>("商品名称");
        goodsNameCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getGoodsName()));
        goodsNameCol.setPrefWidth(180);

        TableColumn<OrderVO, String> countCol = new TableColumn<>("数量");
        countCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(String.valueOf(cd.getValue().getCount())));
        countCol.setPrefWidth(70);

        TableColumn<OrderVO, String> priceCol = new TableColumn<>("总价");
        priceCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(formatPrice(cd.getValue().getTotalPrice())));
        priceCol.setPrefWidth(100);

        TableColumn<OrderVO, String> timeCol = new TableColumn<>("下单时间");
        timeCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getOrderTime()));
        timeCol.setPrefWidth(170);

        orderTable.getColumns().addAll(orderIdCol, userCol, goodsNameCol, countCol, priceCol, timeCol);
        for (TableColumn<OrderVO, ?> col : orderTable.getColumns()) {
            col.setMinWidth(60.0);
        }

        orderTable.setRowFactory(tv -> {
            TableRow<OrderVO> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    showOrderDetail(row.getItem());
                }
            });
            return row;
        });
    }

    /**
     * 异步加载全部订单与统计。
     */
    private void refreshAll() {
        THREAD_POOL.execute(() -> {
            try {
                Message listReq = new Message(currentUser.getAccountNumber(), MessageType.ORDER_LIST_ALL, null, null);
                Message listResp = socketClient.send(listReq);
                Message statsReq = new Message(currentUser.getAccountNumber(), MessageType.ORDER_STATISTICS, null, null);
                Message statsResp = socketClient.send(statsReq);
                Platform.runLater(() -> {
                    if (listResp != null && listResp.getCode() == ResponseCode.SUCCESS
                            && listResp.getData() instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<OrderVO> orders = (List<OrderVO>) listResp.getData();
                        allOrders = orders == null ? new ArrayList<>() : orders;
                        applyFilters();
                    } else {
                        showAlert("读取失败", errorText(listResp, "无法读取订单列表"), Alert.AlertType.ERROR);
                    }
                    if (statsResp != null && statsResp.getCode() == ResponseCode.SUCCESS
                            && statsResp.getData() instanceof StatisticsVO) {
                        renderStats((StatisticsVO) statsResp.getData());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("网络错误", "无法连接服务器: " + e.getMessage(), Alert.AlertType.ERROR));
            }
        });
    }

    /**
     * 按商品名称 / 用户 ID 对缓存订单做本地筛选。
     */
    private void applyFilters() {
        String goodsKw = goodsFilterField.getText() == null ? "" : goodsFilterField.getText().trim().toLowerCase();
        String userKw = userFilterField.getText() == null ? "" : userFilterField.getText().trim().toLowerCase();

        List<OrderVO> filtered = new ArrayList<>();
        for (OrderVO order : allOrders) {
            boolean goodsOk = goodsKw.isEmpty()
                    || (order.getGoodsName() != null && order.getGoodsName().toLowerCase().contains(goodsKw));
            boolean userOk = userKw.isEmpty()
                    || (order.getStudentId() != null && order.getStudentId().toLowerCase().contains(userKw));
            if (goodsOk && userOk) {
                filtered.add(order);
            }
        }
        orderTable.getItems().setAll(filtered);
        tableCountLabel.setText("共 " + filtered.size() + " 条 / 全部 " + allOrders.size() + " 条");
    }

    /**
     * 渲染统计信息。
     */
    private void renderStats(StatisticsVO stats) {
        totalOrdersValue.setText(String.valueOf(stats.getTotalOrders()));
        totalRevenueValue.setText(formatPrice(stats.getTotalRevenue()));

        topProductsBox.getChildren().clear();
        List<StatisticsVO.TopProduct> top = stats.getTopProducts();
        if (top == null || top.isEmpty()) {
            Label none = new Label("暂无数据");
            none.getStyleClass().add("orders-stat-muted");
            topProductsBox.getChildren().add(none);
            return;
        }
        for (int i = 0; i < top.size(); i++) {
            StatisticsVO.TopProduct p = top.get(i);
            HBox line = new HBox(8.0);
            line.setAlignment(Pos.CENTER_LEFT);
            Label rank = new Label("#" + (i + 1));
            rank.getStyleClass().add("orders-rank");
            Label text = new Label(p.getGoodsName() + "  ·  " + p.getTotalCount() + " 件 · "
                    + formatPrice(p.getRevenue()));
            text.getStyleClass().add("orders-stat-muted");
            line.getChildren().addAll(rank, text);
            topProductsBox.getChildren().add(line);
        }
    }

    /**
     * 展示订单详情弹窗。
     */
    private void showOrderDetail(OrderVO order) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("订单详情");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getStylesheets().addAll(getStylesheets());
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("关闭", ButtonBar.ButtonData.CANCEL_CLOSE));

        GridPane grid = new GridPane();
        grid.setHgap(24.0);
        grid.setVgap(12.0);
        grid.setPadding(new Insets(20.0));
        addDetailRow(grid, 0, "订单号", order.getOrderId());
        addDetailRow(grid, 1, "用户 ID", order.getStudentId());
        addDetailRow(grid, 2, "商品编码", order.getGoodsId());
        addDetailRow(grid, 3, "商品名称", order.getGoodsName());
        addDetailRow(grid, 4, "数量", String.valueOf(order.getCount()));
        addDetailRow(grid, 5, "总价", formatPrice(order.getTotalPrice()));
        addDetailRow(grid, 6, "下单时间", order.getOrderTime());

        dialog.getDialogPane().setContent(grid);
        dialog.showAndWait();
    }

    private void addDetailRow(GridPane grid, int row, String title, String value) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("orders-detail-title");
        Label valueLabel = new Label(value == null ? "--" : value);
        valueLabel.getStyleClass().add("orders-detail-value");
        grid.add(titleLabel, 0, row);
        grid.add(valueLabel, 1, row);
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) {
            return "¥ 0.00";
        }
        return "¥ " + price.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 优先展示服务端返回的具体原因，便于定位（如“不支持的请求类型”= 服务端未更新）。
     */
    private String errorText(Message response, String fallback) {
        if (response != null && response.getData() instanceof String) {
            return (String) response.getData();
        }
        return fallback;
    }
    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}