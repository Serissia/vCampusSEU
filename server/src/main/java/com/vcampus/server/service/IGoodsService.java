package com.vcampus.server.service;

import com.vcampus.common.vo.GoodsVO;

import java.util.List;

/**
 * 超市商品业务接口。
 *
 * @author vCampus Team
 */
public interface IGoodsService {

    /**
     * 按关键字查询商品，关键字为空时返回全部商品。
     */
    List<GoodsVO> queryGoods(String keyword);

    /**
     * 新增商品。
     */
    boolean addGoods(GoodsVO goods);

    /**
     * 更新商品。
     */
    boolean updateGoods(GoodsVO goods);

    /**
     * 删除商品。
     */
    boolean deleteGoods(String goodsId);

    /**
     * 强制下架商品（仅管理员调用）。
     */
    boolean offShelf(String goodsId);
}