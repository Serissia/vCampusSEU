package com.vcampus.server.dao;

import com.vcampus.common.vo.GoodsVO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * 超市商品数据访问接口。
 *
 * @author vCampus Team
 */
public interface IGoodsDao {

    /**
     * 按商品编码或名称进行模糊查询，关键字为空时返回全部商品。
     */
    List<GoodsVO> queryGoods(String keyword) throws SQLException;

    /**
     * 按商品编码精确查询商品。
     */
    GoodsVO findById(String goodsId) throws SQLException;

    /**
     * 在同一事务内按商品编码精确查询并锁定商品行（配合结账事务使用）。
     */
    GoodsVO findByIdForUpdate(Connection conn, String goodsId) throws SQLException;

    /**
     * 在同一事务内原子扣减商品库存，库存不足时拒绝。
     */
    boolean decreaseStock(Connection conn, String goodsId, int count) throws SQLException;

    /**
     * 新增商品。
     */
    boolean insertGoods(GoodsVO goods) throws SQLException;

    /**
     * 更新商品信息（商品编码作为业务主键不可变更）。
     */
    boolean updateGoods(GoodsVO goods) throws SQLException;

    /**
     * 删除商品。
     */
    boolean deleteGoods(String goodsId) throws SQLException;

    /**
     * 更新商品上下架状态。
     */
    boolean updateStatus(String goodsId, String status) throws SQLException;
}