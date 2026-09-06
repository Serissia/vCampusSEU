package com.vcampus.client.util;

import com.vcampus.client.config.AppConfig;
import com.vcampus.client.config.AppConfigManager;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.EventTarget;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
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
     * 为指定的 ScrollPane 绑定动态滚轮加速监听。
     *
     * <p>滚轮落在内嵌可滚动控件（表格/列表/嵌套滚动面板等）上时，按偏好倍率驱动该控件自身滚动；
     * 否则驱动外层滚动面板，保证任意一层滚动速度都与偏好设置一致。</p>
     *
     * @param scrollPane 目标滚动面板
     */
    public static void applyCustomScrollSpeed(ScrollPane scrollPane) {
        if (scrollPane == null) {
            return;
        }

        scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isConsumed()) {
                return;
            }
            double deltaY = event.getDeltaY();
            if (deltaY == 0) {
                return;
            }

            Node target = event.getTarget() instanceof Node ? (Node) event.getTarget() : null;
            Node inner = findInnermostScrollable(target, scrollPane);
            double deltaPixels = -deltaY * SPEED_MULTIPLIER.get();

            // 滚动链：内层控件先滚，滚到边界后剩余滚动量转交给外层滚动面板
            double remaining = deltaPixels;
            if (inner != null) {
                remaining = scrollVerticallyBy(inner, remaining);
            }
            remaining = scrollVerticallyBy(scrollPane, remaining);

            if (Math.abs(remaining - deltaPixels) > 0.5) {
                event.consume();
            }
        });
    }

    /**
     * 从滚轮事件目标向上查找「最内层且可纵向滚动」的控件（不含外层滚动面板本身）。
     *
     * @param target 滚轮事件目标
     * @param owner  当前应用加速的外层 ScrollPane
     * @return 可纵向滚动的内嵌控件，未找到返回 null
     */
    private static Node findInnermostScrollable(Node target, ScrollPane owner) {
        Node current = target;
        while (current != null && current != owner) {
            if (canScrollVertically(current)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * 判断某个节点当前是否可纵向滚动（存在垂直溢出）。
     *
     * @param node 待检测节点
     * @return 可纵向滚动返回 true
     */
    private static boolean canScrollVertically(Node node) {
        if (node instanceof ScrollPane) {
            ScrollPane pane = (ScrollPane) node;
            Node content = pane.getContent();
            return content != null
                    && content.getBoundsInLocal().getHeight() > pane.getViewportBounds().getHeight() + 1.0;
        }
        if (node instanceof TableView || node instanceof TreeTableView
                || node instanceof ListView || node instanceof TreeView
                || node instanceof TextArea) {
            ScrollBar bar = findVerticalScrollBar(node);
            return bar != null && bar.isVisible();
        }
        return false;
    }

    /**
     * 按像素增量纵向滚动指定节点（ScrollPane 或内部带滚动条的控件）。
     *
     * @param node        待滚动节点
     * @param deltaPixels 滚动像素增量（正值向下、负值向上）
     * @return 因到达边界而未能滚动的剩余像素量（用于向上层传递形成滚动链）
     */
    private static double scrollVerticallyBy(Node node, double deltaPixels) {
        if (node instanceof ScrollPane) {
            ScrollPane pane = (ScrollPane) node;
            Node content = pane.getContent();
            if (content == null) {
                return deltaPixels;
            }
            double scrollable = content.getBoundsInLocal().getHeight() - pane.getViewportBounds().getHeight();
            if (scrollable <= 0) {
                return deltaPixels;
            }
            double currentPx = pane.getVvalue() * scrollable;
            double targetPx = Math.max(0.0, Math.min(scrollable, currentPx + deltaPixels));
            double applied = targetPx - currentPx;
            pane.setVvalue(targetPx / scrollable);
            return deltaPixels - applied;
        }

        // TableView/ListView/TreeView/TreeTableView/TextArea 等内部用 ScrollBar 承载纵向滚动
        ScrollBar bar = findVerticalScrollBar(node);
        if (bar == null) {
            return deltaPixels;
        }
        double current = bar.getValue();
        double target = Math.max(bar.getMin(), Math.min(bar.getMax(), current + deltaPixels));
        double applied = target - current;
        bar.setValue(target);
        return deltaPixels - applied;
    }

    /**
     * 查找节点内部的垂直滚动条。
     *
     * @param node 目标节点
     * @return 垂直 ScrollBar，未找到返回 null
     */
    private static ScrollBar findVerticalScrollBar(Node node) {
        for (Node child : node.lookupAll(".scroll-bar")) {
            if (child instanceof ScrollBar && ((ScrollBar) child).getOrientation() == Orientation.VERTICAL) {
                return (ScrollBar) child;
            }
        }
        return null;
    }
}
