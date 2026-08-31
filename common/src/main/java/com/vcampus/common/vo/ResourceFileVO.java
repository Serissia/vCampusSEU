package com.vcampus.common.vo;

import java.io.Serializable;

/**
 * 电子资源文件传输对象。
 *
 * <p>用于客户端与服务端之间传输电子资源（PDF）的文件名与字节内容。</p>
 *
 * @author GGbongy
 */
public class ResourceFileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 原始文件名（上传时）或服务器端文件名（下载时） */
    private String fileName;
    /** 文件字节内容 */
    private byte[] data;

    public ResourceFileVO() {
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}
