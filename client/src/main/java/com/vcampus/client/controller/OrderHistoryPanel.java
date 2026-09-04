package com.vcampus.client.controller;

import com.vcampus.client.net.SocketClient;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.OrderVO;
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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

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
 * 我的订单面板（学生等普通买家查看自己的消费订单）。
 *
 * @author vCampus Team
 */
public class OrderHistoryPanel extends VBox {

    private static final ExecutorService THREAD_POOL = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "OrderHistory-Thread-" + threadNumber.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private final SocketClient socketClient = new SocketClient();

    private UserVO currentUser;
    private TableView<OrderVO> orderTable;
    private Label summaryLabel;

    public OrderHistoryPanel() {
        buildUi();
    }

    /**
     * 注入用户上下文并加载订单。
     */
    public void initData(UserVO user) {
        this.currentUser = user;
        refreshOrders();
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

        // 顶部标题卡
        VBox header = new VBox(0.0);
        header.getStyleClass().add("profile-card");

        HBox headerRow = new HBox(12.0);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4.0);
        Label title = new Label("我的订单");
        title.getStyleClass().add("lib-title");
        Label subtitle = new Label("查看你在校园超市的消费记录（最新在前）");
        subtitle.getStyleClass().add("lib-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("刷新");
        refreshBtn.getStyleClass().add("btn-primary-action");
        refreshBtn.setOnAction(e -> refreshOrders());

        headerRow.getChildren().addAll(titleBox, spacer, refreshBtn);
        header.getChildren().add(headerRow);

        // 订单表格卡
        VBox tableCard = new VBox(12.0);
        tableCard.getStyleClass().add("profile-card");
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        Label sectionTitle = new Label("订单列表（双击查看详情）");
        sectionTitle.getStyleClass().add("lib-section-title");

        orderTable = new TableView<>();
        orderTable.getStyleClass().add("lib-table");
        orderTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(orderTable, Priority.ALWAYS);
        setupTable();

        summaryLabel = new Label();
        summaryLabel.getStyleClass().add("orders-summary");

        tableCard.getChildren().addAll(sectionTitle, orderTable, summaryLabel);

        getChildren().addAll(header, tableCard);
    }

    /**
     * 构造订单表格列。
     */
    private void setupTable() {
        TableColumn<OrderVO, String> orderIdCol = new TableColumn<>("订单号");
        orderIdCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getOrderId()));
        orderIdCol.setPrefWidth(190);

        TableColumn<OrderVO, String> goodsNameCol = new TableColumn<>("商品名称");
        goodsNameCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getGoodsName()));
        goodsNameCol.setPrefWidth(220);

        TableColumn<OrderVO, String> countCol = new TableColumn<>("数量");
        countCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(String.valueOf(cd.getValue().getCount())));
        countCol.setPrefWidth(70);

        TableColumn<OrderVO, String> priceCol = new TableColumn<>("总价");
        priceCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(formatPrice(cd.getValue().getTotalPrice())));
        priceCol.setPrefWidth(110);

        TableColumn<OrderVO, String> timeCol = new TableColumn<>("下单时间");
        timeCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getOrderTime()));
        timeCol.setPrefWidth(180);

        orderTable.getColumns().addAll(orderIdCol, goodsNameCol, countCol, priceCol, timeCol);
        for (TableColumn<OrderVO, ?> col : orderTable.getColumns()) {
            col.setMinWidth(60.0);
        }

        // 双击行查看详情
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
     * 异步加载当前用户的订单（服务端已按时间倒序）。
     */
    private void refreshOrders() {
        THREAD_POOL.execute(() -> {
            try {
                Message request = new Message(currentUser.getAccountNumber(), MessageType.ORDER_QUERY, null, null);
                Message response = socketClient.send(request);
                Platform.runLater(() -> {
                    if (response != null && response.getCode() == ResponseCode.SUCCESS
                            && response.getData() instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<OrderVO> orders = (List<OrderVO>) response.getData();
                        orderTable.getItems().setAll(orders);
                        updateSummary(orders);
                    } else {
                        showAlert("读取失败", errorText(response, "无法读取订单"), Alert.AlertType.ERROR);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("网络错误", "无法连接服务器: " + e.getMessage(), Alert.AlertType.ERROR));
            }
        });
    }

    /**
     * 更新底部汇总。
     */
    private void updateSummary(List<OrderVO> orders) {
        int count = 0;
        BigDecimal total = BigDecimal.ZERO;
        if (orders != null) {
            for (OrderVO order : orders) {
                count += order.getCount();
                if (order.getTotalPrice() != null) {
                    total = total.add(order.getTotalPrice());
                }
            }
        }
        int size = orders == null ? 0 : orders.size();
        summaryLabel.setText("共 " + size + " 笔订单 · " + count + " 件商品 · 合计 "
                + formatPrice(total));
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
     * 优先展示服务端返回的具体原因。
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