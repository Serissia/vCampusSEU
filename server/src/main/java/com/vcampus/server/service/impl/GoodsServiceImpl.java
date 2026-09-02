package com.vcampus.server.service.impl;

import com.vcampus.common.vo.GoodsVO;
import com.vcampus.server.dao.IGoodsDao;
import com.vcampus.server.dao.impl.GoodsDaoImpl;
import com.vcampus.server.service.IGoodsService;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * 超市商品业务实现。
 *
 * @author vCampus Team
 */
public class GoodsServiceImpl implements IGoodsService {

    private final IGoodsDao goodsDao = new GoodsDaoImpl();

    /**
     * 查询商品，空关键字转为匹配全部。
     */
    @Override
    public List<GoodsVO> queryGoods(String keyword) {
        try {
            if (keyword == null || "null".equals(keyword)) {
                keyword = "";
            }
            return goodsDao.queryGoods(keyword.trim());
        } catch (SQLException e) {
            throw new RuntimeException("查询商品失败", e);
        }
    }

    /**
     * 新增商品：校验必填字段与数值合法性后写入，默认上架。
     */
    @Override
    public boolean addGoods(GoodsVO goods) {
        try {
            if (!isValid(goods)) {
                return false;
            }
            if (goods.getStatus() == null || goods.getStatus().trim().isEmpty()) {
                goods.setStatus("ON_SHELF");
            }
            return goodsDao.insertGoods(normalize(goods));
        } catch (SQLException e) {
            throw new RuntimeException("新增商品失败", e);
        }
    }

    /**
     * 更新商品。
     */
    @Override
    public boolean updateGoods(GoodsVO goods) {
        try {
            if (!isValid(goods)) {
                return false;
            }
            return goodsDao.updateGoods(normalize(goods));
        } catch (SQLException e) {
            throw new RuntimeException("修改商品失败", e);
        }
    }

    /**
     * 删除商品。
     */
    @Override
    public boolean deleteGoods(String goodsId) {
        try {
            return goodsId != null && !goodsId.trim().isEmpty() && goodsDao.deleteGoods(goodsId.trim());
        } catch (SQLException e) {
            throw new RuntimeException("删除商品失败", e);
        }
    }

    /**
     * 强制下架商品。
     */
    @Override
    public boolean offShelf(String goodsId) {
        try {
            if (goodsId == null || goodsId.trim().isEmpty()) {
                return false;
            }
            GoodsVO goods = goodsDao.findById(goodsId.trim());
            if (goods == null || "OFF_SHELF".equals(goods.getStatus())) {
                return false;
            }
            return goodsDao.updateStatus(goodsId.trim(), "OFF_SHELF");
        } catch (SQLException e) {
            throw new RuntimeException("商品下架失败", e);
        }
    }

    /**
     * 校验商品字段：编号、名称必填，售价非负，库存非负。
     */
    private boolean isValid(GoodsVO goods) {
        return goods != null
                && goods.getGoodsId() != null && !goods.getGoodsId().trim().isEmpty()
                && goods.getGoodsName() != null && !goods.getGoodsName().trim().isEmpty()
                && goods.getPrice() != null && goods.getPrice().compareTo(BigDecimal.ZERO) >= 0
                && goods.getStock() >= 0;
    }

    /**
     * 去除编号、名称与描述首尾空白。
     */
    private GoodsVO normalize(GoodsVO goods) {
        goods.setGoodsId(goods.getGoodsId().trim());
        goods.setGoodsName(goods.getGoodsName().trim());
        if (goods.getDescription() != null) {
            goods.setDescription(goods.getDescription().trim());
        }
        return goods;
    }
}