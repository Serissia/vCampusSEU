package com.vcampus.client.util;

import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

/**
 * Reusable top slide-in toast banner.
 *
 * @author vCampus Team
 */
public final class ToastBannerUtil {

    private static final String TOAST_CONTAINER_STYLE = "toast-banner-container";
    private static final String ICON_CHECK_PATH = "M511.974401 0c-282.75527 0-511.974401 229.219131-511.974401 511.974401 0 282.757318 229.219131 511.974401 511.974401 511.974401 282.757318 0 511.974401-229.217083 511.974401-511.974401C1023.948803 229.219131 794.729672 0 511.974401 0zM805.63063 379.174385 474.510162 710.296901c0 0-0.004096 0.004096-0.010239 0.010239-15.265029 15.269125-38.541433 17.652877-56.31104 7.157402-3.290971-1.945503-6.393536-4.333351-9.219635-7.157402-0.002048-0.004096-0.006144-0.006144-0.006144-0.006144l-190.642884-190.642884c-18.095223-18.095223-18.095223-47.4375 0-65.536819 18.095223-18.095223 47.4375-18.095223 65.532723 0l157.884714 157.884714 298.362298-298.362298c18.097271-18.095223 47.439548-18.095223 65.534771 0C823.725854 331.738933 823.725854 361.079162 805.63063 379.174385z";
    private static final String ICON_ERROR_PATH = "M957.6 872l-432-736c-6.4-10.4-21.6-10.4-27.2 0l-432 736c-6.4 10.4 1.6 24 13.6 24h864c12 0 20-13.6 13.6-24z m-416-104h-64v-64h64v64z m-63.2-128V384h64v256h-64z";
    private static final String ICON_INFO_PATH = "M514.048 54.272q95.232 0 178.688 36.352t145.92 98.304 98.304 145.408 35.84 178.688-35.84 178.176-98.304 145.408-145.92 98.304-178.688 35.84-178.176-35.84-145.408-98.304-98.304-145.408-35.84-178.176 35.84-178.688 98.304-145.408 145.408-98.304 178.176-36.352zM515.072 826.368q26.624 0 44.544-17.92t17.92-43.52q0-26.624-17.92-44.544t-44.544-17.92-44.544 17.92-17.92 44.544q0 25.6 17.92 43.52t44.544 17.92zM567.296 574.464q-1.024-16.384 20.48-34.816t48.128-40.96 49.152-50.688 24.576-65.024q2.048-39.936-8.192-74.752t-33.792-59.904-60.928-39.936-87.552-14.848q-62.464 0-103.936 22.016t-67.072 53.248-35.84 64.512-9.216 55.808q1.024 26.624 16.896 38.912t34.304 12.8 33.792-10.24 15.36-31.232q0-12.288 7.68-30.208t20.992-34.304 32.256-27.648 42.496-11.264q46.08 0 73.728 23.04t25.6 57.856q0 17.408-10.24 32.256t-26.112 28.672-33.792 27.648-33.792 28.672-26.624 32.256-11.776 37.888l1.024 38.912q0 15.36 14.336 29.184t37.888 14.848q23.552-1.024 37.376-15.36t12.8-32.768l0-24.576z";

    private ToastBannerUtil() {
    }

    /**
     * Shows a top slide-in banner. Types: 0 success, 1 error, 2 info.
     */
    public static void showToastBanner(Node anchor, String message, int type) {
        Node parent = anchor == null ? null : anchor.getParent();
        if (!(parent instanceof StackPane overlay)) {
            return;
        }
        VBox toastContainer = findToastContainer(overlay);

        Label text = new Label(message);
        text.getStyleClass().add("toast-banner-text");
        text.setWrapText(true);
        Node icon = createToastIconByType(type);
        icon.setTranslateX(-36);
        HBox banner = new HBox(-16, icon, text);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setPadding(new Insets(12, 14, 12, 4));
        banner.setMinHeight(48);
        banner.getStyleClass().add("toast-banner");
        banner.getStyleClass().add(type == 0 ? "success" : type == 1 ? "error" : "info");
        banner.setMouseTransparent(true);
        banner.setTranslateY(-60);

        toastContainer.getChildren().add(banner);
        TranslateTransition in = new TranslateTransition(Duration.millis(400), banner);
        in.setToY(0);
        in.setOnFinished(e -> {
            PauseTransition pause = new PauseTransition(Duration.millis(1800));
            pause.setOnFinished(ev -> {
                TranslateTransition out = new TranslateTransition(Duration.millis(400), banner);
                out.setToY(-60);
                out.setOnFinished(fin -> {
                    toastContainer.getChildren().remove(banner);
                    if (toastContainer.getChildren().isEmpty()) {
                        overlay.getChildren().remove(toastContainer);
                    }
                });
                out.play();
            });
            pause.play();
        });
        in.play();
    }

    private static VBox findToastContainer(StackPane overlay) {
        for (Node child : overlay.getChildren()) {
            if (child instanceof VBox container && container.getStyleClass().contains(TOAST_CONTAINER_STYLE)) {
                return container;
            }
        }
        VBox container = new VBox(8);
        container.getStyleClass().add(TOAST_CONTAINER_STYLE);
        container.setAlignment(Pos.TOP_CENTER);
        container.setFillWidth(false);
        StackPane.setAlignment(container, Pos.TOP_CENTER);
        container.setMouseTransparent(true);
        overlay.getChildren().add(container);
        return container;
    }

    private static Node createToastIconByType(int type) {
        double scale = 28.0 / 1024.0;
        StackPane wrapper = new StackPane();
        wrapper.setMinSize(28, 28);
        wrapper.setPrefSize(28, 28);
        wrapper.setMaxSize(28, 28);
        if (type == 0) {
            wrapper.getChildren().add(createIconSvg(ICON_CHECK_PATH, scale, "#2E8B57"));
        } else if (type == 1) {
            wrapper.getChildren().add(createIconSvg(ICON_ERROR_PATH, scale, "#F54A45"));
        } else {
            wrapper.getChildren().add(createIconSvg(ICON_INFO_PATH, scale, "#B58500"));
        }
        return wrapper;
    }

    private static SVGPath createIconSvg(String pathData, double scale, String color) {
        SVGPath path = new SVGPath();
        path.setContent(pathData);
        path.setScaleX(scale);
        path.setScaleY(scale);
        path.setFill(Color.web(color));
        return path;
    }
}
