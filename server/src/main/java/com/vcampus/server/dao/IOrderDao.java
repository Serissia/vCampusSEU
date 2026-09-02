package com.vcampus.server.dao;

import com.vcampus.common.vo.OrderVO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * 超市消费订单数据访问接口。
 *
 * @author vCampus Team
 */
public interface IOrderDao {

    /**
     * 在同一事务内写入一条消费订单。
     */
    boolean insertOrder(Connection conn, OrderVO order) throws SQLException;

    /**
     * 查询某学生的全部消费订单。
     */
    List<OrderVO> listByStudent(String studentId) throws SQLException;
}
