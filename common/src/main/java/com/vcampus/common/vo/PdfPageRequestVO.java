package com.vcampus.common.vo;

import java.io.Serializable;

/**
 * PDF 单页渲染请求值对象。
 *
 * <p>客户端请求服务端渲染电子资源（PDF）的某一页时使用，
 * 服务端据此以目标宽度渲染出对应页位图并回传 PNG 字节。</p>
 *
 * @author GGbongy
 */
public class PdfPageRequestVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 电子资源文件名（服务器端存储索引） */
    private String resourceName;
    /** 目标页索引（从 0 开始） */
    private int pageIndex;
    /** 目标渲染宽度（像素） */
    private int width;

    public PdfPageRequestVO() {
    }

    public PdfPageRequestVO(String resourceName, int pageIndex, int width) {
        this.resourceName = resourceName;
        this.pageIndex = pageIndex;
        this.width = width;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public void setPageIndex(int pageIndex) {
        this.pageIndex = pageIndex;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }
}
