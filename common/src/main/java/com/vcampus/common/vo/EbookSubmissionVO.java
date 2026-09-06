package com.vcampus.common.vo;

import java.io.Serializable;

/**
 * 图书馆纯电子书投稿值对象。
 *
 * <p>读者上传电子资源后生成投稿记录，经管理员审核通过后成为可浏览的纯电子书。</p>
 *
 * @author GGbongy
 */
public class EbookSubmissionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 投稿自增 ID */
    private Integer id;
    /** 上传人学号 / 工号 */
    private String uploaderUid;
    /** 书名 */
    private String title;
    /** 作者 */
    private String author;
    /** 出版社 */
    private String publisher;
    /** 简介 / 描述 */
    private String description;
    /** 电子资源文件名（服务器端存储索引） */
    private String resourceFile;
    /** 状态：PENDING / APPROVED / REJECTED */
    private String status;
    /** 审核人 */
    private String reviewer;
    /** 审核意见 */
    private String reviewComment;
    /** 提交时间 */
    private String createdTime;

    public EbookSubmissionVO() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUploaderUid() {
        return uploaderUid;
    }

    public void setUploaderUid(String uploaderUid) {
        this.uploaderUid = uploaderUid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getResourceFile() {
        return resourceFile;
    }

    public void setResourceFile(String resourceFile) {
        this.resourceFile = resourceFile;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReviewer() {
        return reviewer;
    }

    public void setReviewer(String reviewer) {
        this.reviewer = reviewer;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    public String getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(String createdTime) {
        this.createdTime = createdTime;
    }
}
