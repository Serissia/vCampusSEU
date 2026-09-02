package com.vcampus.client.util;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Material 3 / 莫奈色彩提取工具
 *
 * @author Serissia
 */
public final class MonetColorUtil {

    private static final int SAMPLE_SIZE = 64;

    private MonetColorUtil() {
    }

    /**
     * 从指定图像文件中提取莫奈种子主色 (Hex)
     *
     * @param imageFile 图片文件
     * @return Hex 格式颜色值，如 #487A32
     */
    public static String extractSeedColor(File imageFile) {
        if (imageFile == null || !imageFile.exists() || !imageFile.isFile()) {
            return "#487A32";
        }

        try (FileInputStream fis = new FileInputStream(imageFile)) {
            // 降采样读取，节约内存并提升聚类速度
            Image sampledImage = new Image(fis, SAMPLE_SIZE, SAMPLE_SIZE, false, true);
            return extractFromImage(sampledImage);
        } catch (Exception e) {
            System.err.println("[MonetColorUtil] 莫奈取色异常: " + e.getMessage());
            return "#487A32";
        }
    }

    /**
     * 基于色彩空间加权提取主要色彩
     */
    private static String extractFromImage(Image image) {
        PixelReader reader = image.getPixelReader();
        if (reader == null) {
            return "#487A32";
        }

        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        Map<Integer, Integer> colorCounts = new HashMap<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color c = reader.getColor(x, y);
                // 过滤过暗、过亮与过灰的低饱和度背景像素
                if (c.getBrightness() < 0.15 || c.getBrightness() > 0.92 || c.getSaturation() < 0.18) {
                    continue;
                }

                // 量化到 4-bit/通道，合并邻近相似色
                int r = (int) (c.getRed() * 15);
                int g = (int) (c.getGreen() * 15);
                int b = (int) (c.getBlue() * 15);
                int quantized = (r << 8) | (g << 4) | b;

                colorCounts.put(quantized, colorCounts.getOrDefault(quantized, 0) + 1);
            }
        }

        if (colorCounts.isEmpty()) {
            return "#487A32";
        }

        /* 寻找加权得分最高的主导色 */
        
        int bestKey = -1;
        double maxScore = -1.0;

        for (Map.Entry<Integer, Integer> entry : colorCounts.entrySet()) {
            int key = entry.getKey();
            int count = entry.getValue();

            int r = (key >> 8) & 0xF;
            int g = (key >> 4) & 0xF;
            int b = key & 0xF;
            Color c = Color.rgb(r * 17, g * 17, b * 17);

            // 莫奈评分算法：频次权重 * 饱和度权重
            double score = count * (c.getSaturation() * 1.5 + c.getBrightness() * 0.5);
            if (score > maxScore) {
                maxScore = score;
                bestKey = key;
            }
        }

        if (bestKey == -1) {
            return "#487A32";
        }

        int r = ((bestKey >> 8) & 0xF) * 17;
        int g = ((bestKey >> 4) & 0xF) * 17;
        int b = (bestKey & 0xF) * 17;
        return String.format("#%02X%02X%02X", r, g, b);
    }

    /**
     * 辅助方法：生成 Hex 对应的悬停色 (加亮)
     */
    public static String getHoverColor(String hexColor) {
        Color c = Color.web(hexColor);
        Color hover = c.deriveColor(0, 1.0, 1.15, 1.0);
        return toHex(hover);
    }

    /**
     * 辅助方法：生成 Hex 对应的按下色 (加深)
     */
    public static String getPressedColor(String hexColor) {
        Color c = Color.web(hexColor);
        Color pressed = c.deriveColor(0, 1.0, 0.85, 1.0);
        return toHex(pressed);
    }

    /**
     * 辅助方法：生成 Hex 对应的极浅容器背景色
     */
    public static String getLightContainerColor(String hexColor) {
        Color c = Color.web(hexColor);
        Color light = Color.color(
                c.getRed() * 0.12 + 0.88,
                c.getGreen() * 0.12 + 0.88,
                c.getBlue() * 0.12 + 0.88
        );
        return toHex(light);
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }
}