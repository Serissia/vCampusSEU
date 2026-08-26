package com.vcampus.server.dao.impl;

import com.vcampus.common.vo.CourseVO;
import com.vcampus.server.dao.CourseDao;
import com.vcampus.server.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 课程 JDBC 实现。
 *
 * @author xingyi852
 */
public class CourseDaoImpl implements CourseDao {

    /**
     * 按课程代码、名称或教师姓名进行模糊查询。
     */
    @Override
    public List<CourseVO> queryCourses(String keyword) throws SQLException {
        String sql = "SELECT course_id, course_name, credits, teacher_id, teacher_name, "
                + "max_capacity, current_num, open_semester, time_slot, classroom, status "
                + "FROM tbl_course WHERE course_id LIKE ? OR course_name LIKE ? OR teacher_name LIKE ?";
        List<CourseVO> result = new ArrayList<CourseVO>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            // LIKE 参数统一前后加通配符，便于多字段模糊匹配
            String like = "%" + keyword + "%";
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapCourse(rs));
                }
            }
        }
        return result;
    }

    /**
     * 按课程代码精确查询课程。
     */
    @Override
    public CourseVO findByCode(String courseCode) throws SQLException {
        String sql = "SELECT course_id, course_name, credits, teacher_id, teacher_name, "
                + "max_capacity, current_num, open_semester, time_slot, classroom, status "
                + "FROM tbl_course WHERE course_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, courseCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapCourse(rs);
                }
            }
        }
        return null;
    }

    /**
     * 插入课程时同时写入课程余量和状态。
     */
    @Override
    public boolean insertCourse(CourseVO course) throws SQLException {
        String sql = "INSERT INTO tbl_course(course_id, course_name, credits, teacher_id, "
                + "teacher_name, max_capacity, current_num, open_semester, time_slot, classroom, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, course.getCourseCode());
            ps.setString(2, course.getCourseName());
            ps.setDouble(3, course.getCredit());
            ps.setString(4, course.getTeacherId());
            ps.setString(5, course.getTeacherName());
            ps.setInt(6, course.getCapacity());
            ps.setInt(7, course.getSelectedCount());
            ps.setString(8, course.getSemester());
            ps.setString(9, course.getClassTime());
            ps.setString(10, course.getLocation());
            ps.setString(11, course.getStatus());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 更新课程信息。
     */
    @Override
    public boolean updateCourse(CourseVO course) throws SQLException {
        String sql = "UPDATE tbl_course SET course_name=?, credits=?, teacher_id=?, teacher_name=?, "
                + "max_capacity=?, current_num=?, open_semester=?, time_slot=?, classroom=?, status=? "
                + "WHERE course_id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, course.getCourseName());
            ps.setDouble(2, course.getCredit());
            ps.setString(3, course.getTeacherId());
            ps.setString(4, course.getTeacherName());
            ps.setInt(5, course.getCapacity());
            ps.setInt(6, course.getSelectedCount());
            ps.setString(7, course.getSemester());
            ps.setString(8, course.getClassTime());
            ps.setString(9, course.getLocation());
            ps.setString(10, course.getStatus());
            ps.setString(11, course.getCourseCode());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 通过 SQL 原子加减已选人数，避免并发选课时出现超选。
     */
    @Override
    public boolean updateSelectedCount(String courseCode, int delta) throws SQLException {
        String sql = "UPDATE tbl_course SET current_num = current_num + ? WHERE course_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, delta);
            ps.setString(2, courseCode);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 将课程状态改为 DISABLED。
     */
    @Override
    public boolean disableCourse(String courseCode) throws SQLException {
        String sql = "UPDATE tbl_course SET status = '已停开' WHERE course_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, courseCode);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 将 ResultSet 当前行转换为 CourseVO。
     */
    private CourseVO mapCourse(ResultSet rs) throws SQLException {
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
        return course;
    }
}
