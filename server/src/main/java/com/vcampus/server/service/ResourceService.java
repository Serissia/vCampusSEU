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
public class
ResourceService {

    /** 电子资源存放目录（相对于服务端运行目录） */
    private static final String RESOURCE_DIR = "ebooks";

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
}
