package com.vcampus.server.dao.impl;

import com.vcampus.common.vo.OrderVO;
import com.vcampus.common.vo.StatisticsVO;
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
     * 查询全部订单（最新在前，管理员/卖家）。
     */
    @Override
    public List<OrderVO> listAll() throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM tbl_order ORDER BY order_time DESC, order_id DESC";
        List<OrderVO> result = new ArrayList<OrderVO>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapOrder(rs));
                }
            }
        }
        return result;
    }

    /**
     * 统计全部订单：总订单数、总销售额与热门商品 Top3。
     */
    @Override
    public StatisticsVO queryStatistics() throws SQLException {
        StatisticsVO stats = new StatisticsVO();
        try (Connection conn = DBUtil.getConnection()) {
            String countSql = "SELECT COUNT(*), COALESCE(SUM(total_price), 0) FROM tbl_order";
            try (PreparedStatement ps = conn.prepareStatement(countSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stats.setTotalOrders(rs.getLong(1));
                    stats.setTotalRevenue(rs.getBigDecimal(2));
                }
            }
            String topSql = "SELECT goods_name, SUM(`count`) AS total_count, SUM(total_price) AS revenue "
                    + "FROM tbl_order GROUP BY goods_name "
                    + "ORDER BY total_count DESC, revenue DESC LIMIT 3";
            List<StatisticsVO.TopProduct> tops = new ArrayList<StatisticsVO.TopProduct>();
            try (PreparedStatement ps = conn.prepareStatement(topSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StatisticsVO.TopProduct product = new StatisticsVO.TopProduct();
                    product.setGoodsName(rs.getString("goods_name"));
                    product.setTotalCount(rs.getLong("total_count"));
                    product.setRevenue(rs.getBigDecimal("revenue"));
                    tops.add(product);
                }
            }
            stats.setTopProducts(tops);
        }
        return stats;
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
