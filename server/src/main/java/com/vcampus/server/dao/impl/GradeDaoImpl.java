package com.vcampus.server.dao.impl;

import com.vcampus.common.vo.GradeVO;
import com.vcampus.common.vo.GradeScoreVO;
import com.vcampus.server.dao.GradeDao;
import com.vcampus.server.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 成绩 JDBC 实现。
 *
 * @author xingyi852
 */
public class GradeDaoImpl implements GradeDao {

    /**
     * 写入一条成绩记录。
     */
    @Override
    public boolean insert(GradeVO grade) throws SQLException {
        String sql = "INSERT INTO tbl_grade(student_id, course_id, course_name, final_score, gpa, status) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            int gradeId;
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, grade.getStudentId());
                ps.setString(2, grade.getCourseCode());
                ps.setString(3, grade.getCourseName());
                ps.setDouble(4, grade.getFinalScore());
                ps.setDouble(5, grade.getGpa());
                ps.setString(6, grade.getStatus());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("未能获取成绩记录主键");
                    }
                    gradeId = keys.getInt(1);
                }
            }

            insertComponentScores(conn, gradeId, grade);
            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * 更新已有成绩记录。
     */
    @Override
    public boolean update(GradeVO grade) throws SQLException {
        String findIdSql = "SELECT id FROM tbl_grade WHERE student_id = ? AND course_id = ?";
        String updateSql = "UPDATE tbl_grade SET course_name=?, final_score=?, gpa=?, status=? WHERE id=?";

        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            int gradeId;
            try (PreparedStatement findPs = conn.prepareStatement(findIdSql)) {
                findPs.setString(1, grade.getStudentId());
                findPs.setString(2, grade.getCourseCode());
                try (ResultSet rs = findPs.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("成绩记录不存在");
                    }
                    gradeId = rs.getInt("id");
                }
            }

            try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                updatePs.setString(1, grade.getCourseName());
                updatePs.setDouble(2, grade.getFinalScore());
                updatePs.setDouble(3, grade.getGpa());
                updatePs.setString(4, grade.getStatus());
                updatePs.setInt(5, gradeId);
                updatePs.executeUpdate();
            }

            try (PreparedStatement deletePs = conn.prepareStatement(
                    "DELETE FROM tbl_grade_score WHERE grade_id = ?")) {
                deletePs.setInt(1, gradeId);
                deletePs.executeUpdate();
            }

            insertComponentScores(conn, gradeId, grade);
            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * 判断某学生某课程是否已有成绩。
     */
    @Override
    public boolean exists(String studentId, String courseCode) throws SQLException {
        String sql = "SELECT COUNT(*) FROM tbl_grade WHERE student_id = ? AND course_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            ps.setString(2, courseCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * 查询某学生的全部成绩。
     */
    @Override
    public List<GradeVO> listByStudent(String studentId) throws SQLException {
        String sql = "SELECT id, student_id, course_id, course_name, final_score, gpa, status "
                + "FROM tbl_grade WHERE student_id = ?";
        List<GradeVO> result = new ArrayList<GradeVO>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int gradeId = rs.getInt("id");
                    GradeVO grade = mapGrade(rs);
                    loadComponentScores(conn, gradeId, grade);
                    result.add(grade);
                }
            }
        }
        return result;
    }

    /**
     * 查询某课程下所有学生的成绩。
     */
    @Override
    public List<GradeVO> listByCourse(String courseCode) throws SQLException {
        String sql = "SELECT id, student_id, course_id, course_name, final_score, gpa, status "
                + "FROM tbl_grade WHERE course_id = ?";
        List<GradeVO> result = new ArrayList<GradeVO>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, courseCode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int gradeId = rs.getInt("id");
                    GradeVO grade = mapGrade(rs);
                    loadComponentScores(conn, gradeId, grade);
                    result.add(grade);
                }
            }
        }
        return result;
    }

    /**
     * 将 ResultSet 当前行转换为 GradeVO。
     */
    private GradeVO mapGrade(ResultSet rs) throws SQLException {
        GradeVO grade = new GradeVO();
        grade.setStudentId(rs.getString("student_id"));
        grade.setCourseCode(rs.getString("course_id"));
        grade.setCourseName(rs.getString("course_name"));
        grade.setFinalScore(rs.getDouble("final_score"));
        grade.setGpa(rs.getDouble("gpa"));
        grade.setStatus(rs.getString("status"));
        return grade;
    }

    private void insertComponentScores(Connection conn, int gradeId, GradeVO grade) throws SQLException {
        if (grade.getComponentScores() == null || grade.getComponentScores().isEmpty()) {
            return;
        }
        String sql = "INSERT INTO tbl_grade_score(grade_id, component_name, score) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (GradeScoreVO score : grade.getComponentScores()) {
                ps.setInt(1, gradeId);
                ps.setString(2, score.getComponentName());
                ps.setDouble(3, score.getScore());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadComponentScores(Connection conn, int gradeId, GradeVO grade) throws SQLException {
        String sql = "SELECT component_name, score FROM tbl_grade_score WHERE grade_id = ?";
        List<GradeScoreVO> scores = new ArrayList<GradeScoreVO>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, gradeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    scores.add(new GradeScoreVO(
                            rs.getString("component_name"),
                            rs.getDouble("score")));
                }
            }
        }
        grade.setComponentScores(scores);
    }
}
