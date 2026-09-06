package com.vcampus.server.dao.impl;

import com.vcampus.common.vo.NoticeQueryVO;
import com.vcampus.common.vo.NoticeVO;
import com.vcampus.server.dao.NoticeDao;
import com.vcampus.server.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 教务处公告数据访问实现。
 *
 * @author Serissia
 */
public class NoticeDaoImpl implements NoticeDao {

    private static final String NOTICE_COLUMNS = "id, title, publish_date, category, url, crawled_time";

    @Override
    public int batchInsertOrUpdate(List<NoticeVO> notices) throws SQLException {
        if (notices == null || notices.isEmpty()) {
            return 0;
        }
        String sql = "INSERT INTO tbl_notice (title, publish_date, category, url, crawled_time) "
                + "VALUES (?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE title = VALUES(title), publish_date = VALUES(publish_date), category = VALUES(category)";

        int affectedCount = 0;
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (NoticeVO notice : notices) {
                ps.setString(1, notice.getTitle());
                ps.setString(2, notice.getPublishDate());
                ps.setString(3, notice.getCategory());
                ps.setString(4, notice.getUrl());
                ps.setString(5, notice.getCrawledTime());
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            for (int r : results) {
                if (r > 0) {
                    affectedCount += r;
                }
            }
        }
        return affectedCount;
    }

    @Override
    public List<NoticeVO> queryNotices(NoticeQueryVO query) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT " + NOTICE_COLUMNS + " FROM tbl_notice WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (query != null) {
            if (query.getKeyword() != null && !query.getKeyword().trim().isEmpty()) {
                sql.append("AND title LIKE ? ");
                params.add("%" + query.getKeyword().trim() + "%");
            }
            if (query.getStartDate() != null && !query.getStartDate().trim().isEmpty()) {
                sql.append("AND publish_date >= ? ");
                params.add(query.getStartDate().trim());
            }
            if (query.getEndDate() != null && !query.getEndDate().trim().isEmpty()) {
                sql.append("AND publish_date <= ? ");
                params.add(query.getEndDate().trim());
            }
            if (query.getCategory() != null && !query.getCategory().trim().isEmpty()) {
                sql.append("AND category = ? ");
                params.add(query.getCategory().trim());
            }
        }

        sql.append("ORDER BY publish_date DESC, id DESC");

        List<NoticeVO> list = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapNotice(rs));
                }
            }
        }
        return list;
    }

    @Override
    public int countTotalNotices() throws SQLException {
        String sql = "SELECT COUNT(*) FROM tbl_notice";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    @Override
    public String getMeta(String key) throws SQLException {
        String sql = "SELECT meta_value FROM tbl_notice_meta WHERE meta_key = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("meta_value") : null;
            }
        }
    }

    @Override
    public boolean setMeta(String key, String value) throws SQLException {
        String sql = "INSERT INTO tbl_notice_meta (meta_key, meta_value) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE meta_value = VALUES(meta_value)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            return ps.executeUpdate() > 0;
        }
    }

    private NoticeVO mapNotice(ResultSet rs) throws SQLException {
        NoticeVO notice = new NoticeVO();
        notice.setId(rs.getInt("id"));
        notice.setTitle(rs.getString("title"));
        notice.setPublishDate(rs.getString("publish_date"));
        notice.setCategory(rs.getString("category"));
        notice.setUrl(rs.getString("url"));
        notice.setCrawledTime(rs.getString("crawled_time"));
        return notice;
    }
}