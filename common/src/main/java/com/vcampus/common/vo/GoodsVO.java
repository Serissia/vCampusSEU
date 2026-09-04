package com.vcampus.common.vo;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 超市商品信息值对象。
 *
 * @author vCampus Team
 */
public class GoodsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商品编码 */
    private String goodsId;
    /** 商品名称 */
    private String goodsName;
    /** 售价 */
    private BigDecimal price;
    /** 当前库存 */
    private int stock;
    /** 商品描述 */
    private String description;

    /** 商品状态: ON_SHELF 上架, OFF_SHELF 已下架 */
    private String status;
    /** 商品图片文件名（服务端本地存储索引，为空表示未上传，商品展示使用“暂无图片”） */
    private String image;

    public GoodsVO() {
    }

    public String getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(String goodsId) {
        this.goodsId = goodsId;
    }

    public String getGoodsName() {
        return goodsName;
    }

    public void setGoodsName(String goodsName) {
        this.goodsName = goodsName;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
}