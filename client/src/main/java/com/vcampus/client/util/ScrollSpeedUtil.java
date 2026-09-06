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
     * 内嵌控件滚到边界后才轮到外层滚动面板，形成「内层优先、到边转交」的滚动链。</p>
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
            boolean down = deltaY < 0;

            Node target = event.getTarget() instanceof Node ? (Node) event.getTarget() : null;
            Node inner = findInnermostScrollable(target, scrollPane);

            // 内层在该方向还能继续滚 → 只滚内层；否则（到边界或无内层）滚外层
            if (inner != null && canScrollInDirection(inner, down)) {
                scrollNode(inner, down, deltaY);
                event.consume();
            } else if (canScrollInDirection(scrollPane, down)) {
                scrollNode(scrollPane, down, deltaY);
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
     * 判断某个节点在指定方向上是否还能继续滚动（未到达该方向边界）。
     *
     * @param node 待检测节点
     * @param down true 表示向下、false 表示向上
     * @return 还能滚动返回 true
     */
    private static boolean canScrollInDirection(Node node, boolean down) {
        if (node instanceof ScrollPane) {
            ScrollPane pane = (ScrollPane) node;
            Node content = pane.getContent();
            if (content == null) {
                return false;
            }
            double scrollable = content.getBoundsInLocal().getHeight() - pane.getViewportBounds().getHeight();
            if (scrollable <= 0) {
                return false;
            }
            double v = pane.getVvalue();
            return down ? v < 0.999 : v > 0.001;
        }

        ScrollBar bar = findVerticalScrollBar(node);
        if (bar == null) {
            return false;
        }
        return down ? bar.getValue() < bar.getMax() - 0.001 : bar.getValue() > bar.getMin() + 0.001;
    }

    /**
     * 按偏好倍率滚动指定节点。
     *
     * <p>ScrollPane 走归一化 vvalue，TextArea 走 scrollTop（像素），
     * 表格/列表等 VirtualFlow 控件用 increment/decrement 逐行滚动，避免单位换算导致跳变。</p>
     *
     * @param node   待滚动节点
     * @param down   true 表示向下、false 表示向上
     * @param deltaY 本次滚轮事件的纵向增量
     */
    private static void scrollNode(Node node, boolean down, double deltaY) {
        double multiplier = SPEED_MULTIPLIER.get();

        if (node instanceof ScrollPane) {
            ScrollPane pane = (ScrollPane) node;
            Node content = pane.getContent();
            if (content == null) {
                return;
            }
            double scrollable = content.getBoundsInLocal().getHeight() - pane.getViewportBounds().getHeight();
            if (scrollable <= 0) {
                return;
            }
            double deltaPixels = -deltaY * multiplier;
            double deltaV = deltaPixels / scrollable;
            pane.setVvalue(Math.max(0.0, Math.min(1.0, pane.getVvalue() + deltaV)));
            return;
        }

        if (node instanceof TextArea) {
            TextArea area = (TextArea) node;
            double deltaPixels = -deltaY * multiplier;
            area.setScrollTop(Math.max(0.0, area.getScrollTop() + deltaPixels));
            return;
        }

        ScrollBar bar = findVerticalScrollBar(node);
        if (bar == null) {
            return;
        }
        // 一个标准滚轮刻度 ≈ 40px，按 24px/行 换算行数并随倍率放大，上限 20 行/次防止异常跳变
        int rows = (int) Math.max(1, Math.min(20, Math.round(Math.abs(deltaY) * multiplier / 24.0)));
        for (int i = 0; i < rows; i++) {
            if (down) {
                bar.increment();
            } else {
                bar.decrement();
            }
        }
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
