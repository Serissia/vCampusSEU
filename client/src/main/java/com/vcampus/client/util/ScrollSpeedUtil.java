package com.vcampus.client.util;

import com.vcampus.client.config.AppConfig;
import com.vcampus.client.config.AppConfigManager;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.EventTarget;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.TreeView;
import javafx.scene.input.ScrollEvent;

/**
 * 滚轮滚动速度增强与平滑控制工具类。
 * 提供全局可配置的倍率调节，便于后续接入偏好设置。
 *
 * @author Serissia
 */
public final class ScrollSpeedUtil {

    /**
     * 全局滚轮速度倍率属性（默认为 3.0 倍速，推荐 2.5 ~ 4.0）
     * 后续设置模块可直接修改此 Property，全局立即生效
     */
    public static final DoubleProperty SPEED_MULTIPLIER = new SimpleDoubleProperty(
            AppConfigManager.getInstance().getConfig().getScrollSpeedFactor()
    );

    private ScrollSpeedUtil() {
    }

    /**
     * 为指定的 ScrollPane 绑定动态滚轮加速监听
     *
     * @param scrollPane 目标滚动面板
     */
    public static void applyCustomScrollSpeed(ScrollPane scrollPane) {
        if (scrollPane == null) {
            return;
        }

        scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            // 滚轮落在内嵌可滚动控件（表格/列表/嵌套滚动面板等）上时，交由该控件自身处理，
            // 避免外层滚动面板在捕获阶段抢占滚动导致内层表格无法滚动。
            if (isInsideScrollableControl(event.getTarget(), scrollPane)) {
                return;
            }

            double deltaY = event.getDeltaY();
            if (deltaY == 0) {
                return;
            }

            Node content = scrollPane.getContent();
            if (content == null) {
                return;
            }

            // 计算实际可滚动的高度差
            double contentHeight = content.getBoundsInLocal().getHeight();
            double viewportHeight = scrollPane.getViewportBounds().getHeight();
            double scrollableHeight = contentHeight - viewportHeight;

            if (scrollableHeight > 0) {
                // 计算当前单次滚轮应移动的 vvalue 比例
                double deltaV = -deltaY * SPEED_MULTIPLIER.get() / scrollableHeight;
                double newVvalue = Math.max(0.0, Math.min(1.0, scrollPane.getVvalue() + deltaV));

                scrollPane.setVvalue(newVvalue);
                event.consume(); // 拦截默认迟钝的原生滚动逻辑
            }
        });
    }

    /**
     * 判断滚轮事件目标是否落在内嵌的可滚动控件内部（含其祖先链，直至外层滚动面板为止）。
     *
     * @param target 滚轮事件目标
     * @param owner  当前应用加速的外层 ScrollPane
     * @return 若目标位于内嵌可滚动控件内部则返回 true
     */
    private static boolean isInsideScrollableControl(EventTarget target, ScrollPane owner) {
        Node current = target instanceof Node ? (Node) target : null;
        while (current != null && current != owner) {
            if (current instanceof TableView
                    || current instanceof TreeTableView
                    || current instanceof ListView
                    || current instanceof TreeView
                    || current instanceof TextArea
                    || current instanceof ScrollPane) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }
}