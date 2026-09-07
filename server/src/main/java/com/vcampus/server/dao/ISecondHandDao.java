package com.vcampus.server.dao;

import com.vcampus.common.vo.SecondHandVO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * 校园二手市场数据访问接口。
 *
 * @author vCampus Team
 */
public interface ISecondHandDao {

    /**
     * 查询全部在售商品（最新发布在前）。
     */
    List<SecondHandVO> listOnSale() throws SQLException;

    /**
     * 在同一事务内按 id 查询并锁定商品行（购买流程使用）。
     */
    SecondHandVO findByIdForUpdate(Connection conn, int id) throws SQLException;

    /**
     * 发布二手商品（状态默认在售）。
     */
    boolean insert(SecondHandVO vo) throws SQLException;

    /**
     * 在同一事务内将商品标记为已售（购买流程使用）。
     */
    boolean markSoldById(Connection conn, int id) throws SQLException;

    /**
     * 卖家下架自己发布的商品。
     */
    boolean offShelf(String sellerId, int id) throws SQLException;
}