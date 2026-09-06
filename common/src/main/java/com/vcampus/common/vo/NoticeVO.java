package com.vcampus.common.vo;

import java.io.Serial;
import java.io.Serializable;

/**
 * 教务处公告数据值对象。
 *
 * @author Serissia
 */
public class NoticeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 自增主键 ID
     */
    private Integer id;

    /**
     * 公告标题
     */
    private String title;

    /**
     * 公告发布日期（格式：yyyy-MM-dd）
     */
    private String publishDate;

    /**
     * 公告所属栏目（如：教务信息、教学动态等）
     */
    private String category;

    /**
     * 公告原文详情链接
     */
    private String url;

    /**
     * 爬取入库时间（格式：yyyy-MM-dd HH:mm:ss）
     */
    private String crawledTime;

    public NoticeVO() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPublishDate() {
        return publishDate;
    }

    public void setPublishDate(String publishDate) {
        this.publishDate = publishDate;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCrawledTime() {
        return crawledTime;
    }

    public void setCrawledTime(String crawledTime) {
        this.crawledTime = crawledTime;
    }

    @Override
    public String toString() {
        return "NoticeVO{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", publishDate='" + publishDate + '\'' +
                ", category='" + category + '\'' +
                ", url='" + url + '\'' +
                ", crawledTime='" + crawledTime + '\'' +
                '}';
    }
}