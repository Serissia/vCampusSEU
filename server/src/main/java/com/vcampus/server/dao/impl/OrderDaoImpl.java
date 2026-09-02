package com.vcampus.server.dao.impl;

import com.vcampus.common.vo.OrderVO;
import com.vcampus.server.dao.IOrderDao;
import com.vcampus.server.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 超市消费订单 JDBC 实现。
 *
 * @author vCampus Team
 */
public class OrderDaoImpl implements IOrderDao {

    private static final String COLUMNS = "order_id, student_id, goods_id, goods_name, `count`, total_price, order_time";

    /**
     * 在同一事务内写入一条消费订单。
     */
    @Override
    public boolean insertOrder(Connection conn, OrderVO order) throws SQLException {
        String sql = "INSERT INTO tbl_order(order_id, student_id, goods_id, goods_name, `count`, total_price, order_time) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, order.getOrderId());
            ps.setString(2, order.getStudentId());
            ps.setString(3, order.getGoodsId());
            ps.setString(4, order.getGoodsName());
            ps.setInt(5, order.getCount());
            ps.setBigDecimal(6, order.getTotalPrice());
            ps.setString(7, order.getOrderTime());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 查询某学生的全部消费订单。
     */
    @Override
    public List<OrderVO> listByStudent(String studentId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM tbl_order WHERE student_id = ? ORDER BY order_time DESC";
        List<OrderVO> result = new ArrayList<OrderVO>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapOrder(rs));
                }
            }
        }
        return result;
    }

    /**
     * 将 ResultSet 当前行转换为 OrderVO。
     */
    private OrderVO mapOrder(ResultSet rs) throws SQLException {
        OrderVO order = new OrderVO();
        order.setOrderId(rs.getString("order_id"));
        order.setStudentId(rs.getString("student_id"));
        order.setGoodsId(rs.getString("goods_id"));
        order.setGoodsName(rs.getString("goods_name"));
        order.setCount(rs.getInt("count"));
        order.setTotalPrice(rs.getBigDecimal("total_price"));
        order.setOrderTime(rs.getString("order_time"));
        return order;
    }
}
