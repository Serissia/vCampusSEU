package com.vcampus.client.util;

import com.vcampus.client.config.AppConfig;
import com.vcampus.client.config.AppConfigManager;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
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
}