package com.vcampus.client;

import com.vcampus.client.config.AppConfig;
import com.vcampus.client.config.AppConfigManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.awt.AWTException;
import java.awt.Font;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.io.IOException;

/**
 * JavaFX 应用程序主实例
 *
 * @author Serissia
 */
public class ClientApp extends Application {
    private static TrayIcon trayIcon;
    private static boolean trayInitialized = false;

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginView.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 920, 580);
        primaryStage.setTitle("统一身份认证 - vCampusSEU");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        bindCloseBehavior(primaryStage);
        primaryStage.show();
    }

    private void bindCloseBehavior(Stage stage) {
        stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, event -> {
            AppConfig config = AppConfigManager.getInstance().getConfig();
            if ("minimize".equalsIgnoreCase(config.getCloseBehavior()) && SystemTray.isSupported()) {
                event.consume();
                initTray(stage);
                Platform.runLater(stage::hide);
            }
        });
    }

    private synchronized void initTray(Stage stage) {
        if (trayInitialized || !SystemTray.isSupported()) {
            return;
        }
        Platform.setImplicitExit(false);

        SystemTray tray = SystemTray.getSystemTray();
        java.awt.Image image = Toolkit.getDefaultToolkit().getImage(
                ClientApp.class.getResource("/images/logo.png")
        );

        PopupMenu popup = new PopupMenu();

        MenuItem showItem = new MenuItem("Open vCampus");
        showItem.addActionListener(e -> Platform.runLater(() -> {
            stage.show();
            stage.setIconified(false);
            stage.toFront();
        }));

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> {
            tray.remove(trayIcon);
            Platform.exit();
            System.exit(0);
        });

        popup.add(showItem);
        popup.addSeparator();
        popup.add(exitItem);

        trayIcon = new TrayIcon(image, "vCampus 智慧校园", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> Platform.runLater(() -> {
            stage.show();
            stage.setIconified(false);
            stage.toFront();
        }));

        try {
            tray.add(trayIcon);
            trayInitialized = true;
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}