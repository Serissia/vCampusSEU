package com.vcampus.server.dao.impl;

import com.vcampus.common.vo.CourseReviewVO;
import com.vcampus.server.dao.CourseReviewDao;
import com.vcampus.server.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 课程评价 JDBC 实现。
 */
public class CourseReviewDaoImpl implements CourseReviewDao {

    @Override
    public boolean submit(CourseReviewVO review) throws SQLException {
        String sql = "INSERT INTO tbl_course_review(student_id, course_id, rating, comment, anonymous, review_time) "
                + "VALUES (?, ?, ?, ?, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE rating = VALUES(rating), comment = VALUES(comment), "
                + "anonymous = VALUES(anonymous), "
                + "review_time = NOW()";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, review.getStudentId());
            ps.setString(2, review.getCourseId());
            ps.setInt(3, review.getRating());
            ps.setString(4, review.getComment());
            ps.setBoolean(5, review.isAnonymous());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public List<CourseReviewVO> listByCourse(String courseId) throws SQLException {
        String sql = "SELECT r.student_id, u.name AS student_name, r.course_id, r.rating, r.anonymous, "
                + "r.comment, DATE_FORMAT(r.review_time, '%Y-%m-%d %H:%i:%s') AS review_time "
                + "FROM tbl_course_review r "
                + "LEFT JOIN tbl_user u ON u.uid = r.student_id "
                + "WHERE r.course_id = ? ORDER BY r.review_time DESC";
        List<CourseReviewVO> result = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CourseReviewVO review = new CourseReviewVO();
                    review.setStudentId(rs.getString("student_id"));
                    review.setStudentName(rs.getString("student_name"));
                    review.setCourseId(rs.getString("course_id"));
                    review.setRating(rs.getInt("rating"));
                    review.setAnonymous(rs.getBoolean("anonymous"));
                    review.setComment(rs.getString("comment"));
                    review.setReviewTime(rs.getString("review_time"));
                    result.add(review);
                }
            }
        }
        return result;
    }

    @Override
    public boolean delete(String studentId, String courseId) throws SQLException {
        String sql = "DELETE FROM tbl_course_review WHERE student_id = ? AND course_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            ps.setString(2, courseId);
            return ps.executeUpdate() > 0;
        }
    }
}
