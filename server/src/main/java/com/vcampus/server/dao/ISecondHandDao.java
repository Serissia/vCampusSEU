package com.vcampus.server.dao;

import com.vcampus.common.vo.OrderVO;
import com.vcampus.common.vo.SecondHandVO;

import java.math.BigDecimal;
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
     * 查询全部待审核商品（最新发布在前，管理员审核用）。
     */
    List<SecondHandVO> listPending() throws SQLException;

    /**
     * 查询某卖家发布的全部商品（含各审核/交易状态，最新在前）。
     */
    List<SecondHandVO> listBySeller(String sellerId) throws SQLException;

    /**
     * 在同一事务内按 id 查询并锁定商品行（购买流程使用）。
     */
    SecondHandVO findByIdForUpdate(Connection conn, int id) throws SQLException;

    /**
     * 发布二手商品（状态默认待审核）。
     */
    boolean insert(SecondHandVO vo) throws SQLException;

    /**
     * 审核二手商品：将指定商品由待审核改为目标状态（ON_SALE 通过 / REJECTED 拒绝）。
     */
    boolean review(int id, String targetStatus) throws SQLException;

    /**
     * 在同一事务内将商品标记为已售（购买流程使用）。
     */
    boolean markSoldById(Connection conn, int id) throws SQLException;

    /**
     * 卖家下架自己发布的商品。
     */
    boolean offShelf(String sellerId, int id) throws SQLException;

    /**
     * 在同一事务内更新商品价格（卖家改价）。
     */
    boolean updatePrice(Connection conn, int id, BigDecimal price) throws SQLException;

    /**
     * 在同一事务内写入一条调价日志。
     */
    boolean insertPriceLog(Connection conn, int itemId, String sellerId,
                           BigDecimal oldPrice, BigDecimal newPrice, String updateTime) throws SQLException;

    /**
     * 在同一事务内写入一条二手交易订单（购买流程使用）。
     */
    boolean insertOrder(Connection conn, OrderVO order) throws SQLException;
}