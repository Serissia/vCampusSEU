package com.vcampus.server.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PDF 电子资源在线渲染服务。
 *
 * <p>在服务端将存储的电子资源（PDF）按需渲染为单页 PNG 图片，客户端仅拉取
 * 当前页图片，避免整份 PDF 进入客户端内存或落盘。文档按文件名做小容量 LRU
 * 缓存，翻页无需反复解析；渲染方法串行化以保证 {@link PDDocument} 线程安全。</p>
 *
 * @author GGbongy
 */
public class PdfRenderService {

    /** 电子资源存放目录（相对于服务端运行目录） */
    private static final String RESOURCE_DIR = "ebooks";

    /** 同时缓存已打开文档的最大数量 */
    private static final int MAX_CACHED_DOCUMENTS = 3;

    /** 渲染 DPI 上下限，防止异常请求导致位图过大 */
    private static final float MIN_DPI = 50f;
    private static final float MAX_DPI = 200f;

    /** 客户端未提供宽度时的默认渲染宽度 */
    private static final int DEFAULT_WIDTH = 800;

    private final Path baseDir;

    /** accessOrder=true 的 LRU 缓存，淘汰时关闭被逐出的文档 */
    private final Map<String, PDDocument> documentCache =
            new LinkedHashMap<String, PDDocument>(MAX_CACHED_DOCUMENTS, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, PDDocument> eldest) {
                    if (size() > MAX_CACHED_DOCUMENTS) {
                        closeQuietly(eldest.getValue());
                        return true;
                    }
                    return false;
                }
            };

    public PdfRenderService() {
        this.baseDir = Paths.get(RESOURCE_DIR).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new RuntimeException("初始化电子资源目录失败", e);
        }
    }

    /**
     * 获取电子资源的总页数。
     */
    public synchronized int getPageCount(String name) throws IOException {
        return getDocument(name).getNumberOfPages();
    }

    /**
     * 渲染指定页为 PNG 字节。
     *
     * @param name      电子资源文件名
     * @param pageIndex 页索引（从 0 开始）
     * @param width     目标渲染宽度（像素），非法时回退默认宽度
     */
    public synchronized byte[] renderPage(String name, int pageIndex, int width) throws IOException {
        PDDocument doc = getDocument(name);
        if (pageIndex < 0 || pageIndex >= doc.getNumberOfPages()) {
            throw new IllegalArgumentException("页码越界");
        }
        int targetWidth = width <= 0 ? DEFAULT_WIDTH : width;
        float pageWidthPt = doc.getPage(pageIndex).getMediaBox().getWidth();
        float dpi = pageWidthPt <= 0 ? 96f : targetWidth / (pageWidthPt / 72f);
        if (dpi < MIN_DPI) {
            dpi = MIN_DPI;
        } else if (dpi > MAX_DPI) {
            dpi = MAX_DPI;
        }

        PDFRenderer renderer = new PDFRenderer(doc);
        BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /**
     * 获取（必要时加载）指定资源的文档，命中缓存则直接返回。
     */
    private PDDocument getDocument(String name) throws IOException {
        PDDocument cached = documentCache.get(name);
        if (cached != null) {
            return cached;
        }
        Path file = resolveSafe(name);
        PDDocument doc = PDDocument.load(file.toFile());
        documentCache.put(name, doc);
        return doc;
    }

    /**
     * 将资源名解析为绝对路径，仅允许纯文件名，防止路径穿越。
     */
    private Path resolveSafe(String name) throws IOException {
        if (name == null || name.trim().isEmpty()) {
            throw new IOException("资源标识为空");
        }
        String safeName = Paths.get(name).getFileName().toString();
        Path target = baseDir.resolve(safeName).normalize();
        if (!target.startsWith(baseDir) || !Files.exists(target)) {
            throw new IOException("电子资源不存在");
        }
        return target;
    }

    private static void closeQuietly(PDDocument doc) {
        if (doc != null) {
            try {
                doc.close();
            } catch (IOException ignored) {
                // 忽略关闭异常
            }
        }
    }
}
