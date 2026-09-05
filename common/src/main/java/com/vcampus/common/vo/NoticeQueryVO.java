package com.vcampus.common.vo;

import java.io.Serial;
import java.io.Serializable;

/**
 * 公告检索过滤条件值对象。
 *
 * @author Serissia
 */
public class NoticeQueryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 标题检索关键词（支持模糊匹配）
     */
    private String keyword;

    /**
     * 检索起始日期（格式：yyyy-MM-dd）
     */
    private String startDate;

    /**
     * 检索截止日期（格式：yyyy-MM-dd）
     */
    private String endDate;

    /**
     * 栏目分类（为空表示全部栏目）
     */
    private String category;

    public NoticeQueryVO() {
    }

    public NoticeQueryVO(String keyword, String startDate, String endDate) {
        this.keyword = keyword;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    @Override
    public String toString() {
        return "NoticeQueryVO{" +
                "keyword='" + keyword + '\'' +
                ", startDate='" + startDate + '\'' +
                ", endDate='" + endDate + '\'' +
                ", category='" + category + '\'' +
                '}';
    }
}