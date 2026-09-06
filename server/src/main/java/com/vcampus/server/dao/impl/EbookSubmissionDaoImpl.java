package com.vcampus.server.dao.impl;

import com.vcampus.common.vo.EbookSubmissionVO;
import com.vcampus.server.dao.EbookSubmissionDao;
import com.vcampus.server.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 图书馆纯电子书投稿 JDBC 实现。
 *
 * @author GGbongy
 */
public class EbookSubmissionDaoImpl implements EbookSubmissionDao {

    private static final String COLUMNS = "id, uploader_uid, title, author, publisher, description, "
            + "resource_file, status, reviewer, review_comment, created_time";

    @Override
    public boolean insert(EbookSubmissionVO submission) throws SQLException {
        String sql = "INSERT INTO tbl_ebook_submission"
                + "(uploader_uid, title, author, publisher, description, resource_file, status, created_time) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, submission.getUploaderUid());
            ps.setString(2, submission.getTitle());
            ps.setString(3, submission.getAuthor());
            ps.setString(4, submission.getPublisher());
            ps.setString(5, submission.getDescription());
            ps.setString(6, submission.getResourceFile());
            ps.setString(7, submission.getCreatedTime());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public List<EbookSubmissionVO> listByUploader(String uid) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM tbl_ebook_submission WHERE uploader_uid = ? ORDER BY id DESC";
        List<EbookSubmissionVO> result = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapSubmission(rs));
                }
            }
        }
        return result;
    }

    @Override
    public List<EbookSubmissionVO> listPending() throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM tbl_ebook_submission WHERE status = 'PENDING' ORDER BY id DESC";
        List<EbookSubmissionVO> result = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapSubmission(rs));
                }
            }
        }
        return result;
    }

    @Override
    public EbookSubmissionVO findById(int id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM tbl_ebook_submission WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapSubmission(rs);
                }
            }
        }
        return null;
    }

    @Override
    public boolean updateStatus(int id, String status, String reviewer, String reviewComment) throws SQLException {
        String sql = "UPDATE tbl_ebook_submission SET status = ?, reviewer = ?, review_comment = ? WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, reviewer);
            ps.setString(3, reviewComment);
            ps.setInt(4, id);
            return ps.executeUpdate() > 0;
        }
    }

    private EbookSubmissionVO mapSubmission(ResultSet rs) throws SQLException {
        EbookSubmissionVO submission = new EbookSubmissionVO();
        submission.setId(rs.getInt("id"));
        submission.setUploaderUid(rs.getString("uploader_uid"));
        submission.setTitle(rs.getString("title"));
        submission.setAuthor(rs.getString("author"));
        submission.setPublisher(rs.getString("publisher"));
        submission.setDescription(rs.getString("description"));
        submission.setResourceFile(rs.getString("resource_file"));
        submission.setStatus(rs.getString("status"));
        submission.setReviewer(rs.getString("reviewer"));
        submission.setReviewComment(rs.getString("review_comment"));
        submission.setCreatedTime(rs.getString("created_time"));
        return submission;
    }
}
