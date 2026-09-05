package com.vcampus.client.controller;

import com.vcampus.client.util.SvgIcons;
import com.vcampus.common.vo.UserVO;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 用户管理中心控制器（系统管理员「用户权限」入口）。
 *
 * <p>提供注册新用户、用户信息管理、重置密码三个业务入口，点击进入对应功能页，
 * 功能页通过面包屑返回。</p>
 *
 * @author GGbongy
 */
public class UserManageController {

    @FXML
    private StackPane userManageRoot;

    private UserVO currentUser;
    private final Deque<Node> navStack = new ArrayDeque<>();

    public void initData(UserVO user) {
        this.currentUser = user;
        showView(buildHub());
    }

    /**
     * 构建用户管理业务选择中心。
     */
    private Node buildHub() {
        VBox container = new VBox(16.0);
        container.setPadding(new Insets(10.0));
        container.getStyleClass().add("lib-container");

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("profile-card");
        VBox headerText = new VBox(4.0);
        Label title = new Label("用户管理中心");
        title.getStyleClass().add("lib-title");
        Label subtitle = new Label("请选择需要办理的用户管理业务");
        subtitle.getStyleClass().add("lib-subtitle");
        headerText.getChildren().addAll(title, subtitle);
        header.getChildren().add(headerText);

        HBox cards = new HBox(16.0);
        cards.getChildren().addAll(
                createHubCard("注册新用户", "创建新的校园账号", "user", this::showRegister),
                createHubCard("用户信息管理", "编辑信息、冻结、删除", "user-shield", this::showInfoManage),
                createHubCard("重置密码", "为用户设置新密码", "cog", this::showResetPassword));

        container.getChildren().addAll(header, cards);
        return container;
    }

    private Node createHubCard(String title, String desc, String iconKey, Runnable action) {
        VBox card = new VBox(10.0);
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(200.0);
        card.setMinHeight(150.0);
        card.getStyleClass().add("lib-hub-card");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("lib-hub-title");

        Label descLabel = new Label(desc);
        descLabel.getStyleClass().add("lib-subtitle");
        descLabel.setWrapText(true);

        card.getChildren().addAll(SvgIcons.createIcon(iconKey, 28.0, "lib-hub-icon"), titleLabel, descLabel);
        card.setCursor(Cursor.HAND);
        card.setOnMouseClicked(e -> action.run());
        return card;
    }

    private void showRegister() {
        loadSubView("/fxml/UserRegisterView.fxml");
    }

    private void showInfoManage() {
        loadSubView("/fxml/UserInfoView.fxml");
    }

    private void showResetPassword() {
        loadSubView("/fxml/UserPasswordView.fxml");
    }

    private void loadSubView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node root = loader.load();
            Object controller = loader.getController();
            if (controller instanceof UserRegisterController) {
                ((UserRegisterController) controller).initData(currentUser, this::navigateBack);
            } else if (controller instanceof UserInfoController) {
                ((UserInfoController) controller).initData(currentUser, this::navigateBack);
            } else if (controller instanceof UserPasswordController) {
                ((UserPasswordController) controller).initData(currentUser, this::navigateBack);
            }
            navigateTo(root);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showView(Node node) {
        userManageRoot.getChildren().setAll(node);
    }

    private void navigateTo(Node node) {
        if (!userManageRoot.getChildren().isEmpty()) {
            navStack.push(userManageRoot.getChildren().get(0));
        }
        showView(node);
    }

    private void navigateBack() {
        if (!navStack.isEmpty()) {
            showView(navStack.pop());
        }
    }
}
