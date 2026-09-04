package com.vcampus.server.dao;

import com.vcampus.common.vo.CartVO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * 超市购物车数据访问接口。
 *
 * @author vCampus Team
 */
public interface ICartDao {

    /**
     * 加入购物车：已存在同商品时累加数量，否则新增一条。
     */
    boolean addOrIncrease(String studentId, String goodsId, int count, String addTime) throws SQLException;

    /**
     * 更新某条目的数量。
     */
    boolean updateCount(String studentId, String goodsId, int count) throws SQLException;

    /**
     * 移除某条目。
     */
    boolean removeItem(String studentId, String goodsId) throws SQLException;

    /**
     * 清空某用户的购物车。
     */
    boolean clearByStudent(String studentId) throws SQLException;

    /**
     * 在同一事务内清空购物车（结算成功后调用）。
     */
    boolean clearByStudent(Connection conn, String studentId) throws SQLException;

    /**
     * 查询某用户的购物车（联表商品信息）。
     */
    List<CartVO> listByStudent(String studentId) throws SQLException;

    /**
     * 在同一事务内查询并锁定购物车及其商品行（结算使用）。
     */
    List<CartVO> listByStudentForUpdate(Connection conn, String studentId) throws SQLException;
}