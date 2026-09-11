package com.vcampus.server.service.impl;

import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.util.DateUtil;
import com.vcampus.common.vo.OrderVO;
import com.vcampus.common.vo.SecondHandVO;
import com.vcampus.common.vo.UserVO;
import com.vcampus.server.dao.ISecondHandDao;
import com.vcampus.server.dao.UserDao;
import com.vcampus.server.dao.impl.SecondHandDaoImpl;
import com.vcampus.server.dao.impl.UserDaoImpl;
import com.vcampus.server.service.ISecondHandService;
import com.vcampus.server.util.DBUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 校园二手市场业务实现。
 *
 * @author vCampus Team
 */
public class SecondHandServiceImpl implements ISecondHandService {

    private final ISecondHandDao secondHandDao = new SecondHandDaoImpl();
    private final UserDao userDao = new UserDaoImpl();

    @Override
    public List<SecondHandVO> listOnSale() {
        try {
            return secondHandDao.listOnSale();
        } catch (SQLException e) {
            throw new RuntimeException("查询二手商品失败", e);
        }
    }

    @Override
    public List<SecondHandVO> listPending() {
        try {
            return secondHandDao.listPending();
        } catch (SQLException e) {
            throw new RuntimeException("查询待审核商品失败", e);
        }
    }

    @Override
    public List<SecondHandVO> listMine(String uid) {
        try {
            return secondHandDao.listBySeller(uid == null ? "" : uid.trim());
        } catch (SQLException e) {
            throw new RuntimeException("查询我的发布失败", e);
        }
    }

    @Override
    public ResponseCode publish(String uid, SecondHandVO vo) {
        try {
            if (uid == null || vo == null || vo.getTitle() == null || vo.getTitle().trim().isEmpty()
                    || vo.getPrice() == null || vo.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseCode.INVALID_REQUEST;
            }
            UserVO seller = userDao.queryByUid(uid.trim());
            if (seller == null) {
                return ResponseCode.FAIL;
            }
            vo.setSellerId(uid.trim());
            vo.setSellerName(seller.getName() == null ? uid.trim() : seller.getName());
            vo.setTitle(vo.getTitle().trim());
            vo.setStatus("PENDING");
            vo.setCreatedTime(DateUtil.format(new Date()));
            return secondHandDao.insert(vo) ? ResponseCode.SUCCESS : ResponseCode.FAIL;
        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseCode.FAIL;
        }
    }

    @Override
    public ResponseCode offShelf(String uid, Integer id) {
        try {
            if (uid == null || id == null) {
                return ResponseCode.INVALID_REQUEST;
            }
            return secondHandDao.offShelf(uid.trim(), id) ? ResponseCode.SUCCESS : ResponseCode.FAIL;
        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseCode.FAIL;
        }
    }

    @Override
    public ResponseCode review(String uid, Integer id, boolean approve) {
        try {
            if (id == null) {
                return ResponseCode.INVALID_REQUEST;
            }
            String targetStatus = approve ? "ON_SALE" : "REJECTED";
            return secondHandDao.review(id, targetStatus) ? ResponseCode.SUCCESS : ResponseCode.FAIL;
        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseCode.FAIL;
        }
    }

    @Override
    public ResponseCode buy(String uid, Integer id) {
        Connection conn = null;
        try {
            if (uid == null || id == null) {
                return ResponseCode.INVALID_REQUEST;
            }
            String buyerId = uid.trim();

            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            // 锁定商品行，防止并发重复购买
            SecondHandVO item = secondHandDao.findByIdForUpdate(conn, id);
            if (item == null || !"ON_SALE".equals(item.getStatus())) {
                rollback(conn);
                return ResponseCode.SECOND_HAND_SOLD;
            }
            if (item.getSellerId().equals(buyerId)) {
                rollback(conn);
                return ResponseCode.INVALID_REQUEST;
            }

            UserVO buyer = userDao.queryByUidForUpdate(conn, buyerId);
            if (buyer == null) {
                rollback(conn);
                return ResponseCode.FAIL;
            }
            BigDecimal price = item.getPrice() == null ? BigDecimal.ZERO : item.getPrice();
            BigDecimal balance = buyer.getBalance() == null ? BigDecimal.ZERO : buyer.getBalance();
            if (balance.compareTo(price) < 0) {
                rollback(conn);
                return ResponseCode.BALANCE_INSUFFICIENT;
            }

            if (!userDao.deductBalance(conn, buyerId, price)) {
                rollback(conn);
                return ResponseCode.BALANCE_INSUFFICIENT;
            }
            // 货款转入卖家账户
            if (!userDao.creditBalance(conn, item.getSellerId(), price)) {
                rollback(conn);
                return ResponseCode.FAIL;
            }
            if (!secondHandDao.markSoldById(conn, id)) {
                rollback(conn);
                return ResponseCode.SECOND_HAND_SOLD;
            }

            // 写入二手交易订单快照（与货款流转同一事务）
            OrderVO order = new OrderVO();
            order.setOrderId(generateOrderId());
            order.setStudentId(buyerId);
            order.setSellerId(item.getSellerId());
            order.setGoodsId(String.valueOf(item.getId()));
            order.setGoodsName(item.getTitle());
            order.setCount(1);
            order.setTotalPrice(price);
            order.setOrderTime(DateUtil.format(new Date()));
            order.setOrderType("SECOND_HAND");
            if (!secondHandDao.insertOrder(conn, order)) {
                rollback(conn);
                return ResponseCode.FAIL;
            }

            conn.commit();
            return ResponseCode.SUCCESS;
        } catch (SQLException e) {
            rollback(conn);
            e.printStackTrace();
            return ResponseCode.FAIL;
        } finally {
            closeQuietly(conn);
        }
    }

    @Override
    public ResponseCode updatePrice(String uid, Integer id, BigDecimal newPrice) {
        Connection conn = null;
        try {
            if (uid == null || id == null || newPrice == null) {
                return ResponseCode.INVALID_REQUEST;
            }
            BigDecimal min = new BigDecimal("0.01");
            BigDecimal max = new BigDecimal("99999.99");
            if (newPrice.compareTo(min) < 0 || newPrice.compareTo(max) > 0
                    || newPrice.stripTrailingZeros().scale() > 2) {
                return ResponseCode.INVALID_REQUEST;
            }
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            SecondHandVO item = secondHandDao.findByIdForUpdate(conn, id);
            if (item == null || !"ON_SALE".equals(item.getStatus())) {
                rollback(conn);
                return ResponseCode.SECOND_HAND_SOLD;
            }
            if (!uid.trim().equals(item.getSellerId())) {
                rollback(conn);
                return ResponseCode.UNAUTHORIZED;
            }
            String now = DateUtil.format(new Date());
            if (!secondHandDao.insertPriceLog(conn, id, uid.trim(), item.getPrice(), newPrice, now)
                    || !secondHandDao.updatePrice(conn, id, newPrice)) {
                rollback(conn);
                return ResponseCode.FAIL;
            }
            conn.commit();
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
     * 生成二手订单流水号（时间戳 + 随机后缀）。
     */
    private String generateOrderId() {
        return "SHO" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    private void rollback(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
                // ignore
            }
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {
                // ignore
            }
        }
    }
}