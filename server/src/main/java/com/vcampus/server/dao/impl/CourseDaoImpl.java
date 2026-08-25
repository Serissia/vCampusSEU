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
 */
public class CourseDaoImpl implements CourseDao {

    /**
     * 按课程代码、名称或教师姓名进行模糊查询。
     */
    @Override
    public List<CourseVO> queryCourses(String keyword) throws SQLException {
        String sql = "SELECT course_code, course_name, credit, teacher_id, teacher_name, "
                + "capacity, selected_count, semester, class_time, location, status "
                + "FROM course WHERE course_code LIKE ? OR course_name LIKE ? OR teacher_name LIKE ?";
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
        String sql = "SELECT course_code, course_name, credit, teacher_id, teacher_name, "
                + "capacity, selected_count, semester, class_time, location, status "
                + "FROM course WHERE course_code = ?";
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
        String sql = "INSERT INTO course(course_code, course_name, credit, teacher_id, "
                + "teacher_name, capacity, selected_count, semester, class_time, location, status) "
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
        String sql = "UPDATE course SET course_name=?, credit=?, teacher_id=?, teacher_name=?, "
                + "capacity=?, selected_count=?, semester=?, class_time=?, location=?, status=? "
                + "WHERE course_code=?";
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
        String sql = "UPDATE course SET selected_count = selected_count + ? WHERE course_code = ?";
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
        String sql = "UPDATE course SET status = 'DISABLED' WHERE course_code = ?";
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
        course.setCourseCode(rs.getString("course_code"));
        course.setCourseName(rs.getString("course_name"));
        course.setCredit(rs.getDouble("credit"));
        course.setTeacherId(rs.getString("teacher_id"));
        course.setTeacherName(rs.getString("teacher_name"));
        course.setCapacity(rs.getInt("capacity"));
        course.setSelectedCount(rs.getInt("selected_count"));
        course.setSemester(rs.getString("semester"));
        course.setClassTime(rs.getString("class_time"));
        course.setLocation(rs.getString("location"));
        course.setStatus(rs.getString("status"));
        return course;
    }
}
