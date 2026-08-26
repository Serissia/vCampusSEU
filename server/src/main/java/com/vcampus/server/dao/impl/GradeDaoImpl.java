package com.vcampus.server.dao.impl;

import com.vcampus.common.vo.GradeVO;
import com.vcampus.server.dao.GradeDao;
import com.vcampus.server.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
        String sql = "INSERT INTO tbl_grade(student_id, course_id, course_name, usual_score, "
                + "exam_score, final_score, gpa, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, grade.getStudentId());
            ps.setString(2, grade.getCourseCode());
            ps.setString(3, grade.getCourseName());
            ps.setDouble(4, grade.getUsualScore());
            ps.setDouble(5, grade.getExamScore());
            ps.setDouble(6, grade.getFinalScore());
            ps.setDouble(7, grade.getGpa());
            ps.setString(8, grade.getStatus());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 更新已有成绩记录。
     */
    @Override
    public boolean update(GradeVO grade) throws SQLException {
        String sql = "UPDATE tbl_grade SET course_name=?, usual_score=?, exam_score=?, final_score=?, "
                + "gpa=?, status=? WHERE student_id=? AND course_id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, grade.getCourseName());
            ps.setDouble(2, grade.getUsualScore());
            ps.setDouble(3, grade.getExamScore());
            ps.setDouble(4, grade.getFinalScore());
            ps.setDouble(5, grade.getGpa());
            ps.setString(6, grade.getStatus());
            ps.setString(7, grade.getStudentId());
            ps.setString(8, grade.getCourseCode());
            return ps.executeUpdate() > 0;
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
        String sql = "SELECT student_id, course_id, course_name, usual_score, exam_score, "
                + "final_score, gpa, status FROM tbl_grade WHERE student_id = ?";
        List<GradeVO> result = new ArrayList<GradeVO>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapGrade(rs));
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
        String sql = "SELECT student_id, course_id, course_name, usual_score, exam_score, "
                + "final_score, gpa, status FROM tbl_grade WHERE course_id = ?";
        List<GradeVO> result = new ArrayList<GradeVO>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, courseCode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapGrade(rs));
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
        grade.setCourseCode(rs.getString("course_code"));
        grade.setCourseName(rs.getString("course_name"));
        grade.setUsualScore(rs.getDouble("usual_score"));
        grade.setExamScore(rs.getDouble("exam_score"));
        grade.setFinalScore(rs.getDouble("final_score"));
        grade.setGpa(rs.getDouble("gpa"));
        grade.setStatus(rs.getString("status"));
        return grade;
    }
}
