package com.vcampus.server.dao;

import com.vcampus.common.vo.NoticeQueryVO;
import com.vcampus.common.vo.NoticeVO;

import java.sql.SQLException;
import java.util.List;

/**
 * 教务处公告数据访问接口。
 *
 * @author Serissia
 */
public interface NoticeDao {

    /**
     * 批量入库公告数据（遇相同 URL 自动更新）。
     *
     * @param notices 待入库公告列表
     * @return 实际影响行数
     * @throws SQLException 数据库异常
     */
    int batchInsertOrUpdate(List<NoticeVO> notices) throws SQLException;

    /**
     * 组合条件检索公告列表。
     *
     * @param query 查询参数
     * @return 公告列表
     * @throws SQLException 数据库异常
     */
    List<NoticeVO> queryNotices(NoticeQueryVO query) throws SQLException;

    /**
     * 统计本地公告总条数。
     *
     * @return 总条数
     * @throws SQLException 数据库异常
     */
    int countTotalNotices() throws SQLException;

    /**
     * 获取系统元数据键值。
     *
     * @param key 键名
     * @return 键值
     * @throws SQLException 数据库异常
     */
    String getMeta(String key) throws SQLException;

    /**
     * 存储或更新系统元数据。
     *
     * @param key   键名
     * @param value 键值
     * @return 是否成功
     * @throws SQLException 数据库异常
     */
    boolean setMeta(String key, String value) throws SQLException;
}