package com.vcampus.server.service.impl;

import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.util.DateUtil;
import com.vcampus.common.vo.GoodsVO;
import com.vcampus.common.vo.OrderVO;
import com.vcampus.common.vo.UserVO;
import com.vcampus.server.dao.IGoodsDao;
import com.vcampus.server.dao.IOrderDao;
import com.vcampus.server.dao.UserDao;
import com.vcampus.server.dao.impl.GoodsDaoImpl;
import com.vcampus.server.dao.impl.OrderDaoImpl;
import com.vcampus.server.dao.impl.UserDaoImpl;
import com.vcampus.server.service.IOrderService;
import com.vcampus.server.util.DBUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 超市消费订单业务实现。
 *
 * <p>结账在单一数据库事务内完成：锁定商品行与用户行后，依次校验商品存在、
 * 库存充足、余额充足，再原子扣减库存与余额并写入订单，任一环节失败即整体回滚，
 * 保证“扣库存”与“扣余额”要么同时成功、要么同时失败。</p>
 *
 * @author vCampus Team
 */
public class OrderServiceImpl implements IOrderService {

    private final IGoodsDao goodsDao = new GoodsDaoImpl();
    private final IOrderDao orderDao = new OrderDaoImpl();
    private final UserDao userDao = new UserDaoImpl();

    /**
     * 下单结账：库存与余额在同一事务内原子扣减。
     */
    @Override
    public ResponseCode createOrder(OrderVO order) {
        Connection conn = null;
        try {
            if (order == null || order.getGoodsId() == null || order.getGoodsId().trim().isEmpty()
                    || order.getStudentId() == null || order.getStudentId().trim().isEmpty()
                    || order.getCount() <= 0) {
                return ResponseCode.INVALID_REQUEST;
            }

            String goodsId = order.getGoodsId().trim();
            String studentId = order.getStudentId().trim();
            int count = order.getCount();

            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            // 锁定商品行，防止并发超卖
            GoodsVO goods = goodsDao.findByIdForUpdate(conn, goodsId);
            if (goods == null) {
                rollback(conn);
                return ResponseCode.GOODS_NOT_FOUND;
            }
            // 已下架商品不可购买
            if (goods.getStatus() == null || !"ON_SHELF".equals(goods.getStatus())) {
                rollback(conn);
                return ResponseCode.GOODS_NOT_FOUND;
            }
            if (goods.getStock() < count) {
                rollback(conn);
                return ResponseCode.GOODS_STOCK_INSUFFICIENT;
            }

            // 锁定用户行，防止并发下余额被重复扣减
            UserVO user = userDao.queryByUidForUpdate(conn, studentId);
            if (user == null) {
                rollback(conn);
                return ResponseCode.FAIL;
            }

            BigDecimal totalPrice = goods.getPrice().multiply(BigDecimal.valueOf(count))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal balance = user.getBalance() == null ? BigDecimal.ZERO : user.getBalance();
            if (balance.compareTo(totalPrice) < 0) {
                rollback(conn);
                return ResponseCode.BALANCE_INSUFFICIENT;
            }

            // 原子扣减库存与余额
            if (!goodsDao.decreaseStock(conn, goodsId, count)) {
                rollback(conn);
                return ResponseCode.GOODS_STOCK_INSUFFICIENT;
            }
            if (!userDao.deductBalance(conn, studentId, totalPrice)) {
                rollback(conn);
                return ResponseCode.BALANCE_INSUFFICIENT;
            }

            // 写入订单快照
            OrderVO created = new OrderVO();
            created.setOrderId(generateOrderId());
            created.setStudentId(studentId);
            created.setGoodsId(goodsId);
            created.setGoodsName(goods.getGoodsName());
            created.setCount(count);
            created.setTotalPrice(totalPrice);
            created.setOrderTime(DateUtil.format(new Date()));
            if (!orderDao.insertOrder(conn, created)) {
                rollback(conn);
                return ResponseCode.FAIL;
            }

            conn.commit();

            // 回填订单快照，便于客户端展示
            order.setOrderId(created.getOrderId());
            order.setGoodsName(created.getGoodsName());
            order.setTotalPrice(totalPrice);
            order.setOrderTime(created.getOrderTime());
            return ResponseCode.SUCCESS;
        } catch (SQLException e) {
            rollback(conn);
            e.printStackTrace();
            return ResponseCode.FAIL;
        } finally {
            closeQuietly(conn);
        }
    }

    /**
     * 查询某学生的全部消费订单。
     */
    @Override
    public List<OrderVO> listOrders(String studentId) {
        try {
            return orderDao.listByStudent(studentId);
        } catch (SQLException e) {
            throw new RuntimeException("查询订单失败", e);
        }
    }

    /**
     * 生成订单流水号（时间戳 + 随机后缀）。
     */
    private String generateOrderId() {
        return "ORD" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    /**
     * 事务回滚。
     */
    private void rollback(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
                // 忽略回滚异常
            }
        }
    }

    /**
     * 安全关闭事务连接。
     */
    private void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {
                // 忽略关闭异常
            }
        }
    }
}
