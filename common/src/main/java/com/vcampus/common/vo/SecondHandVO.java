package com.vcampus.common.vo;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 校园二手市场商品值对象。
 *
 * @author vCampus Team
 */
public class SecondHandVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商品 ID */
    private Integer id;
    /** 卖家一卡通号 */
    private String sellerId;
    /** 卖家姓名（发布时快照） */
    private String sellerName;
    /** 商品标题 */
    private String title;
    /** 商品描述 */
    private String description;
    /** 定价 */
    private BigDecimal price;
    /** 状态: ON_SALE 在售, SOLD 已售/下架 */
    private String status;
    /** 发布时间 (yyyy-MM-dd HH:mm:ss) */
    private String createdTime;

    public SecondHandVO() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    public String getSellerName() { return sellerName; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedTime() { return createdTime; }
    public void setCreatedTime(String createdTime) { this.createdTime = createdTime; }
}