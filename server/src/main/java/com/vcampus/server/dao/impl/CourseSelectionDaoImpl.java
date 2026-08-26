package com.vcampus.server.dao.impl;

import com.vcampus.common.vo.CourseSelectionVO;
import com.vcampus.common.vo.CourseVO;
import com.vcampus.server.dao.CourseSelectionDao;
import com.vcampus.server.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 选课 JDBC 实现。
 *
 * @author xingyi852
 */
public class CourseSelectionDaoImpl implements CourseSelectionDao {

    /**
     * 写入一条选课记录，选课时间统一保存为数据库时间戳。
     */
    @Override
    public boolean insert(CourseSelectionVO selection) throws SQLException {
        String sql = "INSERT INTO tbl_course_select(student_id, course_id, select_time, status) "
                + "VALUES (?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, selection.getStudentId());
            ps.setString(2, selection.getCourseCode());
            ps.setTimestamp(3, new java.sql.Timestamp(selection.getSelectTime().getTime()));
            ps.setString(4, selection.getStatus());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 根据学生和课程代码删除选课记录。
     */
    @Override
    public boolean delete(String studentId, String courseCode) throws SQLException {
        String sql = "DELETE FROM tbl_course_select WHERE student_id = ? AND course_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            ps.setString(2, courseCode);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 查询是否已有相同的选课记录。
     */
    @Override
    public boolean exists(String studentId, String courseCode) throws SQLException {
        String sql = "SELECT COUNT(*) FROM tbl_course_select WHERE student_id = ? AND course_id = ?";
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
     * 联表查询学生已选课程的完整课程信息。
     */
    @Override
    public List<CourseVO> listByStudent(String studentId) throws SQLException {
        String sql = "SELECT c.course_id, c.course_name, c.credits, c.teacher_id, c.teacher_name, "
                + "c.max_capacity, c.current_num, c.open_semester, c.time_slot, c.classroom, c.status "
                + "FROM tbl_course_select cs JOIN tbl_course c ON cs.course_id = c.course_id "
                + "WHERE cs.student_id = ?";
        List<CourseVO> result = new ArrayList<CourseVO>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                // 课程与选课表联查，学生端课表直接复用 CourseVO
                while (rs.next()) {
                    CourseVO course = new CourseVO();
                    course.setCourseCode(rs.getString("course_id"));
                    course.setCourseName(rs.getString("course_name"));
                    course.setCredit(rs.getDouble("credits"));
                    course.setTeacherId(rs.getString("teacher_id"));
                    course.setTeacherName(rs.getString("teacher_name"));
                    course.setCapacity(rs.getInt("max_capacity"));
                    course.setSelectedCount(rs.getInt("current_num"));
                    course.setSemester(rs.getString("open_semester"));
                    course.setClassTime(rs.getString("time_slot"));
                    course.setLocation(rs.getString("classroom"));
                    course.setStatus(rs.getString("status"));
                    result.add(course);
                }
            }
        }
        return result;
    }
}
