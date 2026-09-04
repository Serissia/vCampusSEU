package com.vcampus.server.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 电子资源存储服务。
 *
 * <p>将客户端上传的电子资源（PDF）保存到服务端本地目录，并支持按文件名读取。</p>
 *
 * @author GGbongy
 */
public class ResourceService {

    /** 电子资源存放目录（相对于服务端运行目录） */
    private static final String RESOURCE_DIR = "ebooks";

    /** 商品图片存放目录（相对于服务端运行目录） */
    private static final String GOODS_IMAGE_DIR = "goods_images";

    
    private final Path baseDir;

    public ResourceService() {
        this.baseDir = Paths.get(RESOURCE_DIR).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new RuntimeException("初始化电子资源目录失败", e);
        }
    }

    /**
     * 保存上传的文件，返回服务器端生成的唯一文件名。
     */
    public String store(byte[] data) {
        String name = UUID.randomUUID().toString() + ".pdf";
        try {
            Files.write(baseDir.resolve(name), data);
            return name;
        } catch (IOException e) {
            throw new RuntimeException("保存电子资源失败", e);
        }
    }

    /**
     * 按文件名读取电子资源字节内容。
     */
    public byte[] load(String name) {
        // 仅允许纯文件名，防止路径穿越
        String safeName = Paths.get(name).getFileName().toString();
        Path target = baseDir.resolve(safeName).normalize();
        if (!target.startsWith(baseDir) || !Files.exists(target)) {
            throw new RuntimeException("电子资源不存在");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new RuntimeException("读取电子资源失败", e);
        }
    }

    /**
     * 按文件名删除电子资源。
     */
    public boolean delete(String name) {
        String safeName = Paths.get(name).getFileName().toString();
        Path target = baseDir.resolve(safeName).normalize();
        if (!target.startsWith(baseDir) || !Files.exists(target)) {
            return false;
        }
        try {
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            return false;
        }
    }

    // ===================== 商品图片存储 =====================

    /**
     * 获取商品图片存放目录（不存在时自动创建）。
     */
    private Path goodsImageDir() {
        Path dir = baseDir.getParent().resolve(GOODS_IMAGE_DIR).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("初始化商品图片目录失败", e);
        }
        return dir;
    }

    /**
     * 保存上传的商品图片，返回服务端生成的唯一文件名。
     *
     * @param data         图片字节内容
     * @param originalName 客户端原始文件名（用于推断扩展名）
     */
    public String storeImage(byte[] data, String originalName) {
        String name = UUID.randomUUID().toString() + inferExtension(originalName);
        try {
            Files.write(goodsImageDir().resolve(name), data);
            return name;
        } catch (IOException e) {
            throw new RuntimeException("保存商品图片失败", e);
        }
    }

    /**
     * 按文件名读取商品图片字节内容。
     */
    public byte[] loadImage(String name) {
        Path dir = goodsImageDir();
        String safeName = Paths.get(name).getFileName().toString();
        Path target = dir.resolve(safeName).normalize();
        if (!target.startsWith(dir) || !Files.exists(target)) {
            throw new RuntimeException("商品图片不存在");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new RuntimeException("读取商品图片失败", e);
        }
    }

    /**
     * 按文件名删除商品图片。
     */
    public boolean deleteImage(String name) {
        Path dir = goodsImageDir();
        String safeName = Paths.get(name).getFileName().toString();
        Path target = dir.resolve(safeName).normalize();
        if (!target.startsWith(dir) || !Files.exists(target)) {
            return false;
        }
        try {
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 从原始文件名推断扩展名，未知时返回空字符串。
     */
    private String inferExtension(String originalName) {
        if (originalName == null) {
            return "";
        }
        String lower = originalName.toLowerCase();
        // 仅允许常见图片扩展名，避免任意文件后缀
        if (lower.endsWith(".png")) return ".png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return ".jpg";
        if (lower.endsWith(".gif")) return ".gif";
        if (lower.endsWith(".bmp")) return ".bmp";
        if (lower.endsWith(".webp")) return ".webp";
        return "";
    }
}
