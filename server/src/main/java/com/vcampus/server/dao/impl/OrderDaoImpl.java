package com.vcampus.server.dao.impl;

import com.vcampus.common.vo.OrderVO;
import com.vcampus.common.vo.StatisticsVO;
import com.vcampus.server.dao.IOrderDao;
import com.vcampus.server.util.DBUtil;

import java.math.BigDecimal;
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
     * 查询某学生的全部消费订单（含超市订单与二手购买订单，最新在前）。
     */
    @Override
    public List<OrderVO> listByStudent(String studentId) throws SQLException {
        List<OrderVO> result = new ArrayList<OrderVO>();
        // 超市消费订单
        String sql = "SELECT " + COLUMNS + " FROM tbl_order WHERE student_id = ? ORDER BY order_time DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapOrder(rs));
                }
            }
        }
        // 二手购买订单（作为买家）
        String secondHandSql = "SELECT order_id, buyer_id, seller_id, item_id, title, price, order_time "
                + "FROM tbl_second_hand_order WHERE buyer_id = ? ORDER BY order_time DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(secondHandSql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapSecondHandOrder(rs));
                }
            }
        }
        result.sort((a, b) -> compareTimeDesc(a.getOrderTime(), b.getOrderTime()));
        return result;
    }

    /**
     * 查询全部订单（最新在前，管理员/卖家），含超市订单与二手订单。
     */
    @Override
    public List<OrderVO> listAll() throws SQLException {
        List<OrderVO> result = new ArrayList<OrderVO>();
        String sql = "SELECT " + COLUMNS + " FROM tbl_order ORDER BY order_time DESC, order_id DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapOrder(rs));
                }
            }
        }
        String secondHandSql = "SELECT order_id, buyer_id, seller_id, item_id, title, price, order_time "
                + "FROM tbl_second_hand_order ORDER BY order_time DESC, order_id DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(secondHandSql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapSecondHandOrder(rs));
                }
            }
        }
        result.sort((a, b) -> compareTimeDesc(a.getOrderTime(), b.getOrderTime()));
        return result;
    }

    /**
     * 统计全部订单：总订单数、总销售额（含二手）与热门商品 Top3（超市商品）。
     */
    @Override
    public StatisticsVO queryStatistics() throws SQLException {
        StatisticsVO stats = new StatisticsVO();
        try (Connection conn = DBUtil.getConnection()) {
            long totalOrders = 0;
            BigDecimal totalRevenue = BigDecimal.ZERO;
            String countSql = "SELECT COUNT(*), COALESCE(SUM(total_price), 0) FROM tbl_order";
            try (PreparedStatement ps = conn.prepareStatement(countSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    totalOrders += rs.getLong(1);
                    totalRevenue = totalRevenue.add(rs.getBigDecimal(2) == null ? BigDecimal.ZERO : rs.getBigDecimal(2));
                }
            }
            String secondHandCountSql = "SELECT COUNT(*), COALESCE(SUM(price), 0) FROM tbl_second_hand_order";
            try (PreparedStatement ps = conn.prepareStatement(secondHandCountSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    totalOrders += rs.getLong(1);
                    totalRevenue = totalRevenue.add(rs.getBigDecimal(2) == null ? BigDecimal.ZERO : rs.getBigDecimal(2));
                }
            }
            stats.setTotalOrders(totalOrders);
            stats.setTotalRevenue(totalRevenue);

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
     * 将 ResultSet 当前行转换为 OrderVO（超市订单）。
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
        order.setOrderType("SUPERMARKET");
        return order;
    }

    /**
     * 将二手订单表当前行转换为 OrderVO（二手交易订单）。
     */
    private OrderVO mapSecondHandOrder(ResultSet rs) throws SQLException {
        OrderVO order = new OrderVO();
        order.setOrderId(rs.getString("order_id"));
        order.setStudentId(rs.getString("buyer_id"));
        order.setGoodsId("SH-" + rs.getInt("item_id"));
        order.setGoodsName(rs.getString("title"));
        order.setCount(1);
        order.setTotalPrice(rs.getBigDecimal("price"));
        order.setOrderTime(rs.getString("order_time"));
        order.setOrderType("SECOND_HAND");
        order.setSellerId(rs.getString("seller_id"));
        return order;
    }

    /**
     * 按下单时间字符串倒序比较（格式 yyyy-MM-dd HH:mm:ss，字典序即时间序）。
     */
    private static int compareTimeDesc(String a, String b) {
        String ta = a == null ? "" : a;
        String tb = b == null ? "" : b;
        return tb.compareTo(ta);
    }
}
