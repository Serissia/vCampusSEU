package com.vcampus.client.controller;

import com.vcampus.client.config.AppConfigManager;
import com.vcampus.client.net.SocketClient;
import com.vcampus.client.util.SvgIcons;
import com.vcampus.client.util.ThemeManager;
import com.vcampus.common.vo.UserRole;
import com.vcampus.common.vo.UserVO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 主界面主控制器，负责侧边导航鉴权、业务面板挂载与全局上下文调度。
 *
 * @author Serissia, GGbongy
 */
public class MainController {

    @FXML
    private ImageView headerLogoView;
    @FXML
    private Label balanceText;
    @FXML
    private Label avatarInitialText;
    @FXML
    private Label userNameText;
    @FXML
    private Label userRoleBadge;
    @FXML
    private VBox navButtonGroup;
    @FXML
    private StackPane contentArea;
    @FXML
    private Button logoutButton;

    private UserVO currentUser;

    @FXML
    private void initialize() {
        if (logoutButton != null) {
            logoutButton.setGraphic(SvgIcons.createIcon("sign-out", 13.0, "header-icon"));
        }
    }

    private final SocketClient socketClient = new SocketClient();
    private final AcademicController academicController = new AcademicController(socketClient);
    private final List<Button> navButtons = new ArrayList<>();

    /**
     * 菜单定义模型（包含图标与模块标识）
     */
    private static class MenuItem {
        private final String title;
        private final String iconKey;
        private final String moduleKey;

        public MenuItem(String title, String iconKey, String moduleKey) {
            this.title = title;
            this.iconKey = iconKey;
            this.moduleKey = moduleKey;
        }
    }

    /**
     * 初始化用户上下文与菜单权限
     *
     * @param user 当前登录用户实体
     */
    public void initUserContext(UserVO user) {
        this.currentUser = user;
        if (user != null) {
            AppConfigManager.getInstance().switchUser(user.getAccountNumber());
        }
        academicController.setUid(user.getAccountNumber());
        renderHeaderInfo();
        buildRoleBasedNavigation();

        // 应用主题样式
        Platform.runLater(() -> {
            if (contentArea != null && contentArea.getScene() != null) {
                ThemeManager.applyTheme(contentArea.getScene());
            }
        });
    }

    /**
     * 更新顶部余额显示
     *
     * @param newBalance 新的余额
     */
    public void updateBalance(BigDecimal newBalance) {
        if (newBalance != null) {
            balanceText.setText("¥ " + newBalance.setScale(2, RoundingMode.HALF_UP).toPlainString());
        } else {
            balanceText.setText("¥ 0.00");
        }
    }

    /**
     * 渲染顶部用户信息（头像首字母、用户名、角色徽章、余额）
     */
    private void renderHeaderInfo() {
        if (currentUser == null) {
            return;
        }

        String displayName = currentUser.getName() != null && !currentUser.getName().trim().isEmpty()
                ? currentUser.getName() : currentUser.getAccountNumber();
        userNameText.setText(displayName);
        avatarInitialText.setText(displayName.substring(0, 1).toUpperCase());

        UserRole role = currentUser.getRole();
        userRoleBadge.setText(role != null ? role.getLabel() : "未知权限");

        updateBalance(currentUser.getBalance());

        // 加载校徽 Logo
        try (InputStream in = getClass().getResourceAsStream("/images/logo.svg")) {
            if (in != null) {
                headerLogoView.setImage(new Image(in));
            }
        } catch (Exception ignored) {
            // 保留默认占位
        }
    }

    /**
     * 根据角色构建左侧导航栏项（Icon + 说明）
     */
    private void buildRoleBasedNavigation() {
        navButtonGroup.getChildren().clear();
        navButtons.clear();

        List<MenuItem> menus = new ArrayList<>();
        UserRole role = currentUser.getRole() != null ? currentUser.getRole() : UserRole.STUDENT;

        // 所有用户固定包含个人中心
        menus.add(new MenuItem("个人中心", "user", "PROFILE"));

        switch (role) {
            case STUDENT:
                menus.add(new MenuItem("学生选课", "graduation-cap", "ACADEMIC_SELECT"));
                menus.add(new MenuItem("我的成绩", "chart-bar", "ACADEMIC_GRADE"));
                menus.add(new MenuItem("虚拟图书馆", "library", "LIBRARY"));
                menus.add(new MenuItem("校园超市", "store", "SHOP"));
                break;
            case TEACHER:
                menus.add(new MenuItem("课程管理", "calendar-alt", "ACADEMIC_TEACHER"));
                menus.add(new MenuItem("成绩登记", "edit", "ACADEMIC_GRADE_SUBMIT"));
                menus.add(new MenuItem("虚拟图书馆", "library", "LIBRARY"));
                break;
            case ACADEMIC_AFFAIRS_TEACHER:
                menus.add(new MenuItem("全校课表", "calendar-alt", "ACADEMIC_MANAGE"));
                menus.add(new MenuItem("开课审批", "edit", "ACADEMIC_APPROVE"));
                break;
            case STORE_MANAGER:
                menus.add(new MenuItem("商品库存", "boxes", "SHOP_MANAGE"));
                menus.add(new MenuItem("流水订单", "receipt", "SHOP_ORDER_MANAGE"));
                break;
            case SELLER:
                menus.add(new MenuItem("校园超市", "store", "SHOP"));
                break;
            case ADMIN:
                menus.add(new MenuItem("全校课表", "calendar-alt", "ACADEMIC_MANAGE"));
                menus.add(new MenuItem("虚拟图书馆", "library", "LIBRARY"));
                menus.add(new MenuItem("校园超市", "store", "SHOP"));
                menus.add(new MenuItem("用户权限", "user-shield", "ADMIN_USER"));
                break;
            default:
                break;
        }

        // 系统设置模块（通用）
        menus.add(new MenuItem("偏好设置", "cog", "SETTINGS"));

        for (int i = 0; i < menus.size(); i++) {
            final MenuItem item = menus.get(i);
            Button btn = new Button(item.title);
            btn.setGraphic(SvgIcons.createNavIcon(item.iconKey));
            btn.getStyleClass().add("nav-item-btn");
            btn.setOnAction(e -> switchModule(item.moduleKey, btn));
            navButtonGroup.getChildren().add(btn);
            navButtons.add(btn);

            // 默认激活第一项
            if (i == 0) {
                switchModule(item.moduleKey, btn);
            }
        }
    }

    /**
     * 切换主内容区模块
     *
     * @param moduleKey 模块唯一标识
     * @param activeBtn 当前激活按钮
     */
    private void switchModule(String moduleKey, Button activeBtn) {
        if (contentArea != null && contentArea.getScene() != null) {
            ThemeManager.applyTheme(contentArea.getScene());
        }

        for (Button btn : navButtons) {
            btn.getStyleClass().remove("active");
        }
        if (activeBtn != null && !activeBtn.getStyleClass().contains("active")) {
            activeBtn.getStyleClass().add("active");
        }

        contentArea.getChildren().clear();
        contentArea.getChildren().add(createModuleNode(moduleKey));
    }

    /**
     * 构建或加载具体子模块视图（目前占位中）
     *
     * @param moduleKey 模块键
     * @return 节点容器
     */
    private Node createModuleNode(String moduleKey) {
        if ("PROFILE".equals(moduleKey)) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ProfileView.fxml"));
                Node profileRoot = loader.load();
                ProfileController controller = loader.getController();
                controller.initData(currentUser, this);
                return profileRoot;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 教务模块统一由 AcademicView.fxml 承载，样式与图书馆系统保持一致
        if (moduleKey.startsWith("ACADEMIC_")) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AcademicView.fxml"));
                Node academicRoot = loader.load();
                AcademicViewController controller = loader.getController();
                controller.initData(moduleKey, currentUser, academicController);
                return academicRoot;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if ("LIBRARY".equals(moduleKey)) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LibraryView.fxml"));
                Node libraryRoot = loader.load();
                LibraryController controller = loader.getController();
                controller.initData(currentUser);
                return libraryRoot;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if ("SETTINGS".equals(moduleKey)) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/SettingsView.fxml"));
                return loader.load();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if ("LIBRARY".equals(moduleKey)) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LibraryView.fxml"));
                Node root = loader.load();
                LibraryController controller = loader.getController();
                controller.initData(currentUser);
                return root;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if ("SHOP".equals(moduleKey)) {
            ShopPanel shopPanel = new ShopPanel();
            shopPanel.initData(currentUser, this);
            return shopPanel;
        }
        // 其他尚未接入的模块仍保留占位卡片
        VBox card = new VBox(16.0);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("placeholder-card");

        Label moduleTitle = new Label("模块组件 [" + moduleKey + "] 正在就绪");
        moduleTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: -fx-text-main;");

        Label desc = new Label("该子模块视图将在下一步开发中无缝接入当前宿主区。");
        desc.setStyle("-fx-font-size: 13px; -fx-text-fill: -fx-text-muted;");

        card.getChildren().addAll(moduleTitle, desc);
        return card;
    }

    /**
     * 处理登出操作并返回登录页
     */
    @FXML
    private void handleLogout() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginView.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) contentArea.getScene().getWindow();
            Scene scene = new Scene(root, 920, 580);
            stage.setTitle("东南大学智慧校园 - vCampusSEU");
            stage.setScene(scene);
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
